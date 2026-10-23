package dev.bitplugg.matesound.data.auth

import dev.bitplugg.matesound.data.local.Prefs

/**
 * SoundCloud no longer hands out API keys, so the app signs in the same way the
 * mobile web player does: a WebView opens SoundCloud, the user logs in, and we
 * read the resulting `oauth_token` from the page's localStorage.
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
          try {
            var keys = ['oauth_token', 'sc_oauth_token'];
            for (var i = 0; i < keys.length; i++) {
              var token = pick(localStorage.getItem(keys[i]));
              if (token) return token;
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

    fun extractToken(raw: String?): String? {
        val value = raw?.trim()?.trim('"') ?: return null
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
