package sy.medpro.watani.security

import android.app.Activity
import android.view.WindowManager

object ContentProtection {
    fun enableScreenshotPrevention(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}
