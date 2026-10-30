package sy.medpro.watani.ui.security

import android.os.Build

/**
 * Emulator detection — Team 3, device-protection branch.
 *
 * STRUCTURE (deliberate): the decision is a PURE function over an injectable
 * props map ([isEmulator]/[emulatorReasons]) so the logic is testable without
 * an Android runtime. [EmulatorCheckAndroid] is only a thin adapter that reads
 * the real [Build] fields and the qemu system properties.
 *
 * BEHAVIOUR: a positive result does NOT kill the app. It blocks RESTRICTED
 * content only (Questions + Library) via [RestrictedContentGate]; free
 * sections keep working. See [RestrictedContentGate] for the user message.
 *
 * HONEST LIMIT: no emulator check is unbreakable. These signals catch stock
 * Android emulators (AVD, Genymotion, VirtualBox-based images) and emulator
 * farms running default images. A determined operator who patches Build
 * props and qemu properties can spoof a real device. This layer raises the
 * cost of emulator farms; it does not make emulation impossible.
 *
 * NOTE — pre-existing code: security/DeviceIntegrityCheck.isEmulator()
 * already checks a subset of these signals inline for library downloads.
 * This module does NOT modify it; it is the testable, gate-wide version
 * the new restricted-content hook uses. A future cleanup may delegate the
 * old check to this one, but that merge is out of scope for this branch.
 */
object EmulatorCheck {

    /** Keys of the props map. Values are used case-insensitively. */
    object Keys {
        const val FINGERPRINT = "fingerprint"
        const val MODEL = "model"
        const val MANUFACTURER = "manufacturer"
        const val BRAND = "brand"
        const val DEVICE = "device"
        const val PRODUCT = "product"
        const val HARDWARE = "hardware"

        /** Value of ro.kernel.qemu ("1" on emulators) or ro.hardware. */
        const val QEMU = "qemu"
        const val RO_HARDWARE = "ro_hardware"
    }

    /** Pure decision: true when ANY emulator signal matches. */
    fun isEmulator(props: Map<String, String?>): Boolean =
        emulatorReasons(props).isNotEmpty()

    /** Pure decision with reasons (stable strings, for tests and logging). */
    fun emulatorReasons(props: Map<String, String?>): List<String> {
        val reasons = mutableListOf<String>()
        fun v(key: String): String = props[key]?.lowercase() ?: ""
        fun contains(key: String, vararg needles: String): Boolean =
            needles.any { v(key).contains(it) }

        if (contains(Keys.FINGERPRINT, "generic", "sdk", "emulator", "vbox", "genymotion")) {
            reasons += "fingerprint"
        }
        if (contains(Keys.MODEL, "google_sdk", "emulator", "android sdk built for", "sdk")) {
            reasons += "model"
        }
        if (contains(Keys.MANUFACTURER, "genymotion")) {
            reasons += "manufacturer"
        }
        if (contains(Keys.BRAND, "generic", "generic_x86")) {
            reasons += "brand"
        }
        if (contains(Keys.DEVICE, "generic", "google_sdk", "emulator", "vbox86p")) {
            reasons += "device"
        }
        if (contains(Keys.PRODUCT, "sdk_google", "google_sdk", "sdk_gphone", "sdk", "vbox86p", "emulator")) {
            reasons += "product"
        }
        if (contains(Keys.HARDWARE, "goldfish", "ranchu", "vbox86")) {
            reasons += "hardware"
        }
        // qemu kernel property is the strongest single signal when readable.
        if (v(Keys.QEMU) == "1") {
            reasons += "qemu"
        }
        if (contains(Keys.RO_HARDWARE, "goldfish", "ranchu")) {
            reasons += "ro_hardware"
        }
        return reasons
    }
}

/** Thin Android adapter: reads real props, contains no decision logic. */
object EmulatorCheckAndroid {

    fun readProps(): Map<String, String?> {
        val qemu = readSystemProperty("ro.kernel.qemu")
        val roHardware = readSystemProperty("ro.hardware")
        return mapOf(
            EmulatorCheck.Keys.FINGERPRINT to Build.FINGERPRINT,
            EmulatorCheck.Keys.MODEL to Build.MODEL,
            EmulatorCheck.Keys.MANUFACTURER to Build.MANUFACTURER,
            EmulatorCheck.Keys.BRAND to Build.BRAND,
            EmulatorCheck.Keys.DEVICE to Build.DEVICE,
            EmulatorCheck.Keys.PRODUCT to Build.PRODUCT,
            EmulatorCheck.Keys.HARDWARE to Build.HARDWARE,
            EmulatorCheck.Keys.QEMU to qemu,
            EmulatorCheck.Keys.RO_HARDWARE to roHardware,
        )
    }

    fun isEmulator(): Boolean = EmulatorCheck.isEmulator(readProps())

    fun reasons(): List<String> = EmulatorCheck.emulatorReasons(readProps())

    /** android.os.SystemProperties is hidden API; reflection keeps this best-effort. */
    private fun readSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            (method.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
