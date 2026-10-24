package br.com.bloqueiaads.core

/** Conservative starter list; domains must be reviewed before production use. */
object FilterRules {
    private val blocked = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adnxs.com",
        "adsrvr.org",
        "adservice.google.com",
        "googletagservices.com"
    )

    fun isBlocked(hostname: String): Boolean {
        val host = hostname.trim().trimEnd('.').lowercase()
        if (host.isEmpty()) return false
        return blocked.any { host == it || host.endsWith(".$it") }
    }
}
