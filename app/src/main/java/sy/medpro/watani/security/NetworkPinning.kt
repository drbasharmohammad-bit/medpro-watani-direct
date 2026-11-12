package sy.medpro.watani.security

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/**
 * Phase-2 hardening (Nov 2026): SPKI certificate pinning for the two
 * content/gateway hosts. Pins were computed from the LIVE chains
 * fetched 2026-11-13 (openssl s_client -showcerts, SPKI = SHA-256 of
 * the SubjectPublicKeyInfo DER, base64 — the OkHttp pin format).
 *
 * Live chain (both hosts, Let's Encrypt):
 *  - leaf CN=*.workers.dev / CN=watani-content.pages.dev, issued by YE2,
 *    expires 2027-01-08 (workers.dev) / 2027-01-11 (pages.dev). Leaf
 *    certificates rotate roughly every 60-90 days.
 *  - intermediate "YE2" (valid 2025-09-03 → 2028-09-02).
 *  - "Root YE" (valid 2026-05-13 → 2032-09-02).
 *
 * Each host carries THREE pins (leaf + intermediate + root). OkHttp
 * accepts the connection when ANY pin matches ANY certificate in the
 * chain, so a routine leaf rotation does NOT break the app: the YE2
 * intermediate pin keeps matching. ROTATION PROCEDURE (before the YE2
 * intermediate expires in Sep 2028, or if Let's Encrypt changes the
 * issuing intermediate):
 *   1. Fetch the live chain: openssl s_client -connect <host>:443
 *      -servername <host> -showcerts
 *   2. Compute the new SPKI pins and ADD them here alongside the old
 *      ones; ship an app update.
 *   3. Remove the old pins only after the updated build is adopted.
 * EMERGENCY KILL SWITCH: if pinning ever blocks legitimate traffic
 * (e.g. an unannounced chain change), set [PINNING_ENABLED] to false
 * and ship a build — clients then connect unpinned exactly as before.
 * The live-connection proof for these exact pins (a JVM OkHttp client
 * configured with this pinner completing HTTPS requests to both
 * hosts) is recorded in watani-hardening/reports/phase2-app.md.
 */
object NetworkPinning {

    /** Emergency kill switch — see the class docs above. */
    const val PINNING_ENABLED = true

    const val WORKER_HOST = "watani-api.drbasharmohammad.workers.dev"
    const val CONTENT_HOST = "watani-content.pages.dev"

    // Leaf pins (rotate ~90 days; kept current as defence in depth —
    // the intermediate pin below is what guarantees continuity).
    private const val WORKER_LEAF_PIN = "sha256/0FrwmEdN2P8f0ujdH7xIGjjTxRALUJdMP/5uwUmV40A="
    private const val CONTENT_LEAF_PIN = "sha256/ZQTGHv3RAVNVISIir50buw98e/LoYzFE4UCH/Dfozrc="

    // Shared Let's Encrypt chain pins (both hosts use the same chain).
    private const val INTERMEDIATE_YE2_PIN = "sha256/s/tdAOmUzd8syaTuqfgGvFcn6DzA5Cmb+Vby1ST+U3Y="
    private const val ROOT_YE_PIN = "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w="

    val pinner: CertificatePinner = CertificatePinner.Builder()
        .add(WORKER_HOST, WORKER_LEAF_PIN, INTERMEDIATE_YE2_PIN, ROOT_YE_PIN)
        .add(CONTENT_HOST, CONTENT_LEAF_PIN, INTERMEDIATE_YE2_PIN, ROOT_YE_PIN)
        .build()

    /** Applies the pinner unless the kill switch is off. Hosts other
     * than the two pinned ones are unaffected by a CertificatePinner. */
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (PINNING_ENABLED) builder.certificatePinner(pinner)
        return builder
    }
}
