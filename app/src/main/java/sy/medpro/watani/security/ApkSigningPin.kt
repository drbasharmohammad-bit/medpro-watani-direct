package sy.medpro.watani.security

/**
 * The one release signing certificate of the 1.2.x line, as a
 * lowercase-hex SHA-256 of the certificate DER bytes.
 *
 * Verified from the PUBLISHED 1.2.38 (74312) binary itself during
 * the 21 Nov 2026 full audit (APK Signature Scheme v2 block parsed
 * out of the shipped file; SHA-1 of the same certificate is
 * 7b59d90d1a3d7369fa18ab3dd53ff1443329c1dc, the fingerprint on
 * record for the whole 1.2.x line). The release keystore is stable
 * — CI signs every release build with it — so this pin can finally
 * be filled in: the audit findings SEC-R1 / SEC-C7 (TamperCheck
 * unconfigured) and SEC-C1 (update APK installed with no signature
 * verification) both close on this constant.
 *
 * Consumers:
 *  - TamperCheck (ui.security): a running build whose certificate
 *    does not match this pin is treated as a repack and blocked.
 *  - AppUpdate (data): a downloaded update APK whose certificate
 *    does not match this pin is deleted and never handed to the
 *    system installer.
 *
 * Change this ONLY together with a signing-key rotation, and only
 * after the rotated build is the one being verified.
 */
object ApkSigningPin {
    const val SHA256: String =
        "be41d53fcdb0e96739bb445efdf0c453d7fd0957888567dc5a92aea1b189c1b2"
}
