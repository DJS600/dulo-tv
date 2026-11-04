package site.dulo.tv

/**
 * Network-level blocklist. Any request whose host contains one of these
 * fragments is answered with an empty body, so the ad script never runs
 * and therefore never gets the chance to call window.open().
 */
object AdBlock {

    private val HOST_FRAGMENTS = listOf(
        // Google ad stack
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.", "googletagservices.com", "googletagmanager.com",
        "2mdn.net", "google-analytics.com", "analytics.google.com",
        // Pop-under / pop-up networks
        "popads.net", "popcash.net", "propellerads", "propeller-tracking",
        "onclickads", "onclckds", "adsterra", "highperformanceformat.com",
        "hilltopads", "popmyads", "clickadu", "adcash", "exdynsrv.com",
        "displaycontentnetwork", "revenuehits", "yllix", "adf.ly",
        // Adult-heavy networks commonly used by streaming sites
        "exoclick", "juicyads", "trafficjunky", "trafficfactory",
        "adnium", "tsyndicate", "creative.sascdn",
        // Native / content recommendation
        "mgid.com", "taboola.com", "outbrain.com", "revcontent.com",
        "zedo.com", "adskeeper", "media.net",
        // Display exchanges
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "criteo.com",
        "criteo.net", "casalemedia.com", "33across.com", "sharethrough.com",
        "smartadserver.com", "improvedigital.com", "adform.net",
        "openx.net", "sovrn.com", "gumgum.com", "teads.tv", "yieldmo.com",
        "triplelift.com", "districtm.io", "spotxchange.com", "telaria.com",
        "advertising.com", "amazon-adsystem.com", "bidswitch.net",
        "contextweb.com", "lijit.com", "sonobi.com", "trustx.org",
        // Misc trackers that ride along with the ads
        "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "facebook.net/en_US/fbevents", "connect.facebook.net",
        // Redirect interstitials
        "cloudflareinsights.com", "onesignal.com"
    )

    /** Hosts we must never block, even if a fragment matches. */
    private val ALLOW = listOf(
        "dulo.site", "dulo.tv", "dulo.sx", "dulo.mov", "dulo.vg",
        "dulo.watch", "dulo.im", "dulotv.site",
        "youtube.com", "ytimg.com", "googlevideo.com", "gstatic.com",
        "jsdelivr.net", "cdnjs.cloudflare.com", "unpkg.com"
    )

    fun isAd(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = try {
            java.net.URI(url).host?.lowercase()
        } catch (_: Exception) {
            return false
        } ?: return false

        if (ALLOW.any { host == it || host.endsWith(".$it") }) return false
        if (host.startsWith("fonts.") || host.startsWith("cdn.")) {
            // cdn./fonts. hosts are almost always assets the site needs
            if (!HOST_FRAGMENTS.any { host.contains(it) }) return false
        }
        return HOST_FRAGMENTS.any { host.contains(it) }
    }

    /** Bare-domain navigation target (for redirect blocking). */
    fun isAdDestination(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return isAd(url)
    }
}