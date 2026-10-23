package dev.bitplugg.matesound.data.auth

import dev.bitplugg.matesound.data.local.Prefs

/**
 * SoundCloud no longer hands out API keys, so the app signs in the same way the
 * web player does: a WebView opens SoundCloud, the user logs in, and we read the
 * resulting `oauth_token` (an HttpOnly cookie, plus localStorage as a fallback).
 */
object AuthUrls {
    fun loginPage(): String = "https://m.soundcloud.com/"

    fun connectUrl(clientId: String, redirectUri: String): String =
        "https://soundcloud.com/connect" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=token" +
            "&scope=non-expiring"

    /**
     * Tries every place the token can be hiding in a logged-in SoundCloud page.
     */
    const val TOKEN_PROBE_JS: String = """
        (function() {
          function pick(value) {
            if (!value) return '';
            if (value === 'null' || value === 'undefined') return '';
            return String(value);
          }
          function looksLikeToken(key, value) {
            if (!value) return false;
            var k = String(key || '').toLowerCase();
            return k.indexOf('token') >= 0 && k.indexOf('oauth') >= 0;
          }
          try {
            var storages = [localStorage, sessionStorage];
            for (var s = 0; s < storages.length; s++) {
              var storage = storages[s];
              for (var i = 0; i < storage.length; i++) {
                var key = storage.key(i);
                if (!key) continue;
                var value = pick(storage.getItem(key));
                if (!value) continue;
                if (looksLikeToken(key, value)) return value;
              }
              var direct = ['oauth_token', 'sc_oauth_token', 'access_token'];
              for (var j = 0; j < direct.length; j++) {
                var token = pick(storage.getItem(direct[j]));
                if (token) return token;
              }
            }
          } catch (e) {}
          try {
            var match = document.cookie.match(/(?:^|;\s*)oauth_token=([^;]+)/);
            if (match) return decodeURIComponent(match[1]);
          } catch (e) {}
          try {
            var href = location.href;
            var hash = href.split('#')[1] || '';
            var query = href.split('?')[1] || '';
            var params = new URLSearchParams(hash || query);
            var fromUrl = pick(params.get('access_token'));
            if (fromUrl) return fromUrl;
          } catch (e) {}
          return '';
        })()
    """

    /**
     * Reads `oauth_token` from a Cookie header. The SoundCloud cookie is
     * HttpOnly, so `document.cookie` in the WebView cannot see it; the native
     * CookieManager can.
     */
    fun tokenFromCookie(cookieHeader: String?): String? {
        if (cookieHeader.isNullOrBlank()) return null
        for (part in cookieHeader.split(';')) {
            val name = part.substringBefore('=', "").trim()
            if (name != "oauth_token" && name != "sc_oauth_token") continue
            val raw = part.substringAfter('=', "").trim()
            if (raw.isEmpty()) continue
            // Cookie values are percent-encoded; '+' is a literal plus in a
            // cookie, so protect it from URLDecoder's form-decoding rules.
            val decoded = runCatching {
                java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
            }.getOrDefault(raw)
            extractToken(decoded)?.let { return it }
        }
        return null
    }

    fun extractToken(raw: String?): String? {
        var value = raw?.trim()?.trim('"')?.trim('\'') ?: return null
        if (value.startsWith("OAuth ", ignoreCase = true)) {
            value = value.substring(6).trim()
        }
        if (value.contains('=')) {
            // The user may have pasted the whole cookie ("oauth_token=...").
            for (part in value.split(';', '&')) {
                val name = part.substringBefore('=', "").trim()
                if (name == "oauth_token" || name == "sc_oauth_token") {
                    value = part.substringAfter('=', "").trim().trim('"')
                    break
                }
            }
        }
        if (value.isEmpty() || value == "null" || value == "undefined") return null
        if (value.length < 20) return null
        if (value.contains(' ')) return null
        return value
    }

    fun tokenFromUrl(url: String): String? {
        val fragment = url.substringAfter('#', "")
        val query = url.substringAfter('?', "")
        for (part in listOf(fragment, query)) {
            if (part.isBlank()) continue
            for (segment in part.split('&')) {
                val key = segment.substringBefore('=')
                if (key == "access_token" || key == "oauth_token") {
                    return extractToken(segment.substringAfter('=', ""))
                }
            }
        }
        return null
    }
}

class AuthRepository(private val prefs: Prefs) {
    suspend fun saveToken(token: String) {
        prefs.setToken(token.trim())
    }

    suspend fun logout() {
        prefs.setToken(null)
        prefs.setAccount(null, null, null)
    }
}
