package com.eepiemi.materialbook.utils

import java.net.URL
import java.net.URLDecoder

fun fbRedirectSanitizer(link: String): String {
    try {
        var url = URL(link)

        if (url.host == "l.facebook.com" && url.path == "/l.php") {
            val params = url.query.split("&").associate {
                val (key, value) = it.split("=", limit = 2)
                key to URLDecoder.decode(value, "UTF-8")
            }
            url = URL(params["u"] ?: return link)
        }

        // URL's query is already decoded; re-encoding double-encoded it ("%20" -> "%2520")
        // and broke the link. Only fbclid has to go.
        val params = url.query
            ?.split("&")
            ?.filter { !it.startsWith("fbclid=") }
            ?.joinToString("&")

        return buildString {
            append("${url.protocol}://${url.host}")
            if (url.port != -1 && url.port != url.defaultPort) append(":${url.port}")
            append(url.path)
            if (!params.isNullOrBlank()) append("?").append(params)
            // Fragments matter for SPA/news anchors; dropping them lands on the wrong spot.
            if (url.ref != null) append("#").append(url.ref)
        }
    } catch (_: Exception) {
        return link
    }
}

