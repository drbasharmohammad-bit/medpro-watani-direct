package sy.medpro.watani.ui.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Tamper / repack detection — Team 3, device-protection branch.
 *
 * WHAT IS CHECKED (and what each check may do):
 *  1. Package name match — BLOCKS when the running package is not the
 *     expected one (sy.medpro.watani). A repack under another id fails here.
 *  2. Signature presence / consistency — BLOCKS when no signing certificate
 *     can be read at all. Presence-only: it proves the APK is signed, not
 *     that it is OUR signature (see pinned-signature limit below).
 *  3. Pinned signature — BLOCKS on mismatch against
 *     [PINNED_SIGNATURE_SHA256]. The pin was filled in by the
 *     21 Nov 2026 full audit (SEC-R1/SEC-C7): the 1.2.x line DOES
 *     have a stable release keystore, and the pinned digest was
 *     verified from the published 74312 binary itself.
 *  4. Installer source — WARNING ONLY, never blocks. The doctor distributes
 *     builds directly (e.g. via Telegram), so a null installer is a
 *     legitimate install here, not evidence of tampering.
 *  5. Debuggable flag — WARNING ONLY by default ([treatDebuggableAsBlock]
 *     is false), because CI debug builds are debuggable BY DESIGN. A
 *     release build should flip it to true; see the wiring note in
 *     RestrictedContentGate.
 *
 * HONEST LIMIT — READ BEFORE RELYING ON THIS FILE:
 * The pin below was filled in on 21 Nov 2026 (full audit, SEC-R1):
 * the earlier "ephemeral CI keystore" premise no longer holds — the
 * 1.2.x release line is signed with one stable keystore, and the
 * pinned SHA-256 was extracted from the published 74312 APK's own
 * signature block. A repack signed with the attacker's own key now
 * FAILS this check (pin MISMATCH blocks). Local/CI builds signed
 * with any other key (e.g. a debug keystore) also report MISMATCH
 * and are blocked from restricted content by the gate — intended:
 * only the release key opens restricted content.
 * Nothing here is "unhackable": a repacker who also patches this check out
 * of the client bypasses it entirely; server-side checks would be needed
 * for stronger guarantees, and they are out of scope for this branch.
 *
 * STRUCTURE: pure [evaluate] over injectable inputs + thin Android adapter
 * ([TamperCheckAndroid]) that reads PackageManager state.
 */
object TamperCheck {

    /**
     * SHA-256 (lowercase hex) of the release signing certificate —
     * the single source of truth is [sy.medpro.watani.security.ApkSigningPin]
     * (verified from the published 74312 binary, 21 Nov 2026 audit).
     * An empty value would mean UNCONFIGURED (never blocks); the pin
     * is filled, so a mismatch BLOCKS.
     */
    const val PINNED_SIGNATURE_SHA256: String =
        sy.medpro.watani.security.ApkSigningPin.SHA256

    enum class PinStatus { UNCONFIGURED, MATCH, MISMATCH, ABSENT }

    data class Verdict(
        /** True only for package mismatch, absent signature, or pin mismatch. */
        val blocked: Boolean,
        val blockReason: String?,
        val warnings: List<String>,
        val pinStatus: PinStatus,
    )

    /** Pure comparison hook for the pinned signature. Null = unconfigured. */
    fun pinStatusFor(actualSha256Hex: String?): PinStatus {
        if (PINNED_SIGNATURE_SHA256.isBlank()) return PinStatus.UNCONFIGURED
        if (actualSha256Hex.isNullOrBlank()) return PinStatus.ABSENT
        return if (actualSha256Hex.lowercase() == PINNED_SIGNATURE_SHA256.lowercase()) {
            PinStatus.MATCH
        } else {
            PinStatus.MISMATCH
        }
    }

    /** Pure decision over injectable inputs. */
    fun evaluate(
        expectedPackage: String,
        actualPackage: String,
        installerPackage: String?,
        isDebuggable: Boolean,
        signatureSha256Hex: String?,
        treatDebuggableAsBlock: Boolean = false,
    ): Verdict {
        val warnings = mutableListOf<String>()
        val pinStatus = pinStatusFor(signatureSha256Hex)

        if (actualPackage != expectedPackage) {
            return Verdict(
                blocked = true,
                blockReason = "package_mismatch",
                warnings = warnings,
                pinStatus = pinStatus,
            )
        }
        if (signatureSha256Hex.isNullOrBlank()) {
            return Verdict(
                blocked = true,
                blockReason = "signature_absent",
                warnings = warnings,
                pinStatus = PinStatus.ABSENT,
            )
        }
        if (pinStatus == PinStatus.MISMATCH) {
            return Verdict(
                blocked = true,
                blockReason = "signature_pin_mismatch",
                warnings = warnings,
                pinStatus = pinStatus,
            )
        }
        if (pinStatus == PinStatus.UNCONFIGURED) {
            warnings += "signature_pin_unconfigured"
        }
        if (installerPackage == null) {
            warnings += "installer_unknown_sideload"
        }
        if (isDebuggable) {
            if (treatDebuggableAsBlock) {
                return Verdict(
                    blocked = true,
                    blockReason = "debuggable_build",
                    warnings = warnings,
                    pinStatus = pinStatus,
                )
            }
            warnings += "debuggable_build"
        }
        return Verdict(
            blocked = false,
            blockReason = null,
            warnings = warnings,
            pinStatus = pinStatus,
        )
    }
}

/** Thin Android adapter: reads PackageManager state, contains no decisions. */
object TamperCheckAndroid {

    /** The one legitimate package id. Hardcoded on purpose: in a repack,
     * context.packageName IS the repack's id, so comparing the package
     * against itself could never fail. */
    const val EXPECTED_PACKAGE: String = "sy.medpro.watani"

    fun evaluate(
        context: Context,
        treatDebuggableAsBlock: Boolean = false,
    ): TamperCheck.Verdict {
        val pm = context.packageManager
        val expected = EXPECTED_PACKAGE
        val installer = readInstallerPackage(context, pm)
        val isDebuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val signatureHex = readFirstSignatureSha256(context, pm)
        return TamperCheck.evaluate(
            expectedPackage = expected,
            actualPackage = context.packageName,
            installerPackage = installer,
            isDebuggable = isDebuggable,
            signatureSha256Hex = signatureHex,
            treatDebuggableAsBlock = treatDebuggableAsBlock,
        )
    }

    private fun readInstallerPackage(context: Context, pm: PackageManager): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFirstSignatureSha256(context: Context, pm: PackageManager): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            val first = certs?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(first.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
