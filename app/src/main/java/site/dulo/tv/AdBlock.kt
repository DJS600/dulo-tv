package site.dulo.tv

object AdBlock {

    private val HOST_FRAGMENTS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.", "googletagservices.com", "googletagmanager.com",
        "2mdn.net", "google-analytics.com",
        "popads.net", "popcash.net", "propellerads", "propeller-tracking",
        "onclickads", "onclckds", "adsterra", "highperformanceformat.com",
        "hilltopads", "popmyads", "clickadu", "adcash", "exdynsrv.com",
        "revenuehits", "yllix", "bidsxchange", "adserv", "trafficstars",
        "exoclick", "juicyads", "trafficjunky", "trafficfactory",
        "adnium", "tsyndicate", "sascdn",
        "mgid.com", "taboola.com", "outbrain.com", "revcontent.com",
        "zedo.com", "adskeeper", "media.net",
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "criteo.com",
        "criteo.net", "casalemedia.com", "33across.com", "sharethrough.com",
        "smartadserver.com", "improvedigital.com", "adform.net",
        "openx.net", "sovrn.com", "gumgum.com", "teads.tv", "yieldmo.com",
        "triplelift.com", "districtm.io", "spotxchange.com", "telaria.com",
        "advertising.com", "amazon-adsystem.com", "bidswitch.net",
        "contextweb.com", "lijit.com", "sonobi.com", "trustx.org",
        "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "connect.facebook.net", "cloudflareinsights.com", "onesignal.com",
        "push-notifications", "interstitial", "adroll", "adcolony"
    )

    /** Site hosts plus every player/embed/CDN host the site legitimately navigates to. */
    private val ALLOW_NAV = listOf(
        "dulo.site", "dulo.tv", "dulo.sx", "dulo.mov", "dulo.vg",
        "dulo.watch", "dulo.im", "dulotv.site",
        "youtube.com", "ytimg.com", "googlevideo.com", "gstatic.com",
        "jsdelivr.net", "cdnjs.cloudflare.com", "unpkg.com",
        "tmdb.org", "themoviedb.org", "imdb.com",
        "vidsrc.xyz", "vidsrc.to", "vidsrc.me", "vidsrc.net", "vidsrc.in",
        "2embed.cc", "2embed.to", "multiembed.mov", "videasy.net",
        "vidlink.pro", "vidfast.pro", "111movies.com", "smashystream.com",
        "streamtape.com", "filemoon.sx", "dood.", "voe.sx", "mixdrop.co",
        "upstream.to", "mp4upload.com", "vidcloud", "rabbitstream.net",
        "megacloud", "hydrax.net", "streamwish.to", "abyss.to",
        "superembed", "vidbinge.com", "embed.su", "player4u.xyz",
        "cloudflare.com", "b-cdn.net", "fastly.net", "akamaized.net"
    )

    private fun hostOf(url: String?): String? = try {
        java.net.URI(url ?: return null).host?.lowercase()
    } catch (_: Exception) { null }

    private fun matches(host: String, list: List<String>) =
        list.any { host == it || host.endsWith(".$it") || host.contains(it) }

    fun isAd(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        if (matches(host, ALLOW_NAV)) return false
        return matches(host, HOST_FRAGMENTS)
    }

    fun isAdDestination(url: String?) = isAd(url)

    /** True when a navigation to this host should never be treated as an ad redirect. */
    fun isAllowedNav(url: String?): Boolean {
        val u = url ?: return false
        if (u.contains(".m3u8") || u.contains(".mp4")) return true
        val host = hostOf(u) ?: return true
        return matches(host, ALLOW_NAV) || matches(host, HOST_FRAGMENTS).not()
    }
}