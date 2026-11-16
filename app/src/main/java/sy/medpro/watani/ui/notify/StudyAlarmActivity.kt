package sy.medpro.watani.ui.notify

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.MainActivity
import sy.medpro.watani.data.DailyGoalEngine
import sy.medpro.watani.notify.ReminderScheduler
import sy.medpro.watani.notify.StudyNotifications
import sy.medpro.watani.ui.theme.GoldAccent
import sy.medpro.watani.ui.theme.MaroonDeep
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.WataniTheme

/**
 * صفحة منبّه الدراسة بملء الشاشة (بناء 1.2.30).
 *
 * تفتحها قصديّة ملء الشاشة في إشعار تذكير الدراسة فوق شاشة القفل
 * (showWhenLocked + turnScreenOn) حين يفعّل الطالب «المنبّه
 * القوي» ويكون إذن ملء الشاشة متاحًا. تعرض هدف اليوم الحالي
 * وثلاثة أفعال: بدء الدراسة (يفتح التطبيق)، تأجيل عشر دقائق
 * (منبّه لمرة واحدة)، وإغلاق. كل زر يزيل إشعار التذكير الأصلي.
 * الصفحة معلوماتية فقط — لا بوابات ولا محتوى مقيّدًا فيها.
 */
class StudyAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        val snapshot = try {
            DailyGoalEngine.snapshot(applicationContext)
        } catch (_: Exception) {
            null
        }
        val goalLine = if (snapshot == null || !snapshot.hasBank) {
            "افتح التطبيق وتابع تحضيرك للامتحان الوطني."
        } else if (snapshot.goal <= 0) {
            "واصل مراجعتك اليوم وحافظ على إيقاعك."
        } else if (snapshot.doneToday >= snapshot.goal) {
            "أنجزت هدف اليوم كاملًا (${snapshot.goal} سؤالًا) — جلسة إضافية تثبّت تقدمك."
        } else {
            "هدف اليوم: ${snapshot.goal} سؤالًا — أنجزت ${snapshot.doneToday} حتى الآن."
        }
        setContent {
            WataniTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(MaroonDeep, MaroonPrimary))
                            )
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "حان وقت الدراسة",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            goalLine,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(36.dp))
                        Button(
                            onClick = { startStudy() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent,
                                contentColor = MaroonDeep
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("ابدأ الدراسة الآن", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { snooze() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("تأجيل ١٠ دقائق", color = Color.White, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { dismissOnly() }) {
                            Text("إغلاق", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    private fun startStudy() {
        StudyNotifications.dismissStudyReminder(this)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }

    private fun snooze() {
        StudyNotifications.dismissStudyReminder(this)
        ReminderScheduler.scheduleSnooze(this)
        finish()
    }

    private fun dismissOnly() {
        StudyNotifications.dismissStudyReminder(this)
        finish()
    }
}
