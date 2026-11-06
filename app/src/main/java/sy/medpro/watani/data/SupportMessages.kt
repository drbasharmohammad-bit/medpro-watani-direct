package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** رسالة دعم نفسي واحدة من بنك الدعم v1 (40 رسالة، 8 لكل تصنيف). */
data class SupportMessage(
    val id: String,
    val categoryId: String,
    val text: String
)

data class SupportCategory(val id: String, val title: String)

/**
 * بنك رسائل الدعم والطمأنينة v1 — النصوص حرفية من مواصفة التأليف
 * (build-checks/support-bank-v1.md) بلا أي تعديل. الدوران اليومي:
 * index = LocalDate.now().toEpochDay() % 40 على قائمة [all] بترتيب
 * التصنيفات أدناه.
 */
object SupportMessages {
    val categories: List<SupportCategory> = listOf(
        SupportCategory("exhaustion", "تعب وإنهاك"),
        SupportCategory("anxiety", "قلق وتوتر"),
        SupportCategory("self_doubt", "شك بالنفس"),
        SupportCategory("exam_near", "قرب الامتحان"),
        SupportCategory("momentum", "دفعة إنجاز")
    )

    fun categoryTitle(categoryId: String): String =
        categories.firstOrNull { it.id == categoryId }?.title ?: ""

    val all: List<SupportMessage> = listOf(
        SupportMessage("exhaustion-1", "exhaustion", "التعب الذي تشعر به الآن ليس علامة ضعف؛ إنه إثبات أنك بذلت جهدًا حقيقيًا. خذ استراحة قصيرة، فالعقل المُجهد لا يحفظ، والعقل المرتاح لا ينسى بسهولة."),
        SupportMessage("exhaustion-2", "exhaustion", "لا يُطلب منك أن تدرس وأنت منهك. نم جيدًا الليلة، وما تراجعه بنصف تركيز اليوم ستثبّته غدًا بتركيز كامل."),
        SupportMessage("exhaustion-3", "exhaustion", "حتى الأطباء الكبار الذين تراهم اليوم مرّوا بأيام أثقل من يومك. الفرق الوحيد أنهم لم يتوقفوا عند أول يوم صعب."),
        SupportMessage("exhaustion-4", "exhaustion", "جسدك شريكك في هذا الامتحان لا خصمك: كوب ماء، وقفة قصيرة، ونَفَس عميق… ثم عودة هادئة إلى المكتب."),
        SupportMessage("exhaustion-5", "exhaustion", "إذا شعرت أن رأسك امتلأ، فهذا لا يعني أنك نسيت؛ المعلومات تستقر أثناء الراحة كما تستقر أثناء الدراسة."),
        SupportMessage("exhaustion-6", "exhaustion", "يوم خفيف لا يهدم شهورًا من العمل. اسمح لنفسك بإيقاع أبطأ اليوم، وستعوّض غدًا بطاقة أصفى."),
        SupportMessage("exhaustion-7", "exhaustion", "أنت لا تحتاج إلى طاقة خارقة، تحتاج فقط إلى أن تنام وتأكل وتتحرك قليلًا. الأساسيات الصغيرة هي التي تحمل المشاريع الكبيرة."),
        SupportMessage("exhaustion-8", "exhaustion", "أغلق الكتاب عندما يتحول التعب إلى دوران بلا فائدة. التوقف المؤقت قرار ذكي، لا استسلام."),
        SupportMessage("anxiety-1", "anxiety", "القلق قبل الامتحان شعور طبيعي يمر به كل مجتهد؛ إنه يعني أن الأمر يهمّك، لا أنك غير جاهز."),
        SupportMessage("anxiety-2", "anxiety", "لا تحاول أن تهدّئ كل أفكارك دفعة واحدة. ركّز على الساعة القادمة فقط: صفحة واحدة، موضوع واحد، خطوة واحدة."),
        SupportMessage("anxiety-3", "anxiety", "تذكّر: الامتحان يقيس ما تعرفه في يوم واحد، ولا يلخّص قيمتك كطبيب ولا سنوات تعبك كلها."),
        SupportMessage("anxiety-4", "anxiety", "عندما يشتد التوتر، عد إلى ما أنجزته فعلًا: مواضيع أنهيتها، أسئلة أتقنتها، أيام صعبة تجاوزتها. سجلّك أقوى من خوفك."),
        SupportMessage("anxiety-5", "anxiety", "التنفس البطيء ليس رفاهية: شهيق هادئ وزفير أطول، ثلاث مرات، ثم أكمل. دقائق القلق لا تسرق يومك إن لم تمنحها إيّاه."),
        SupportMessage("anxiety-6", "anxiety", "مقارنة نفسك بزملائك أسرع طريق للتوتر. لكل طالب إيقاعه وخطته، وسباقك الحقيقي مع نسختك من الأمس."),
        SupportMessage("anxiety-7", "anxiety", "إن مررت بسؤال لا تعرفه أثناء المراجعة، فهذا خبر جيد: وجدت ثغرة اليوم، لا يوم الامتحان."),
        SupportMessage("anxiety-8", "anxiety", "الخوف من النسيان طبيعي، لكن ذاكرتك أعمق مما تظن؛ المعلومة التي راجعتها بتركيز ستعود إليك ساعة تحتاجها."),
        SupportMessage("self_doubt-1", "self_doubt", "شكّك في نفسك لا يعني أنك ضعيف؛ أكثر الطلاب اجتهادًا هم أكثرهم مراجعة لأنفسهم. الفرق أنهم يكملون رغم الشك."),
        SupportMessage("self_doubt-2", "self_doubt", "لست مطالبًا بأن تعرف كل شيء. أنت مطالب بأن تعرف ما يكفي لتنجح وتتقدّم — وهذا ما تبنيه كل يوم."),
        SupportMessage("self_doubt-3", "self_doubt", "لو كان الطريق سهلًا لما كان لهذا النجاح معنى. وجودك هنا، في هذه المرحلة، دليل أنك قطعت الأصعب."),
        SupportMessage("self_doubt-4", "self_doubt", "أخطاؤك في التدريب ليست حكمًا عليك؛ إنها الخريطة التي ترشدك إلى ما يحتاج تقوية. كل طبيب ناجح مرّ من هنا."),
        SupportMessage("self_doubt-5", "self_doubt", "لا تقس يومًا سيئًا بمقياس مسيرتك كلها. يوم واحد لا يمحو شهورًا، وسؤال واحد لا يلغي مئات الأسئلة التي أجبتها صوابًا."),
        SupportMessage("self_doubt-6", "self_doubt", "أنت تدرس مهنة عظيمة اخترتها بنفسك. في الأيام الثقيلة، تذكّر لماذا بدأت، ودَع السبب يحملك خطوة أخرى."),
        SupportMessage("self_doubt-7", "self_doubt", "الثقة لا تأتي قبل العمل؛ تأتي بعده. كل جلسة تنهيها، صغيرة كانت أم كبيرة، تضيف لبنة إلى ثقتك بنفسك."),
        SupportMessage("self_doubt-8", "self_doubt", "إن شعرت أن الجميع يتقدّم أسرع منك، فتذكّر أنك ترى نتائجهم الظاهرة ولا ترى لياليهم الصعبة."),
        SupportMessage("exam_near-1", "exam_near", "في الأيام الأخيرة، الهدوء أهم من أي معلومة جديدة. راجع ما تعرفه وثبّته، فالثبات يوم الامتحان نصف العلامة."),
        SupportMessage("exam_near-2", "exam_near", "لا تفتح مواضيع جديدة كليًا في اللحظات الأخيرة؛ ما بنيته طوال الشهور هو رصيدك الحقيقي، ومراجعته بهدوء تكفي."),
        SupportMessage("exam_near-3", "exam_near", "جهّز أغراضك وخطتك لليوم الكبير من الليلة السابقة، ونم مبكرًا. عقل مرتاح صباح الامتحان يساوي ساعات مراجعة إضافية."),
        SupportMessage("exam_near-4", "exam_near", "في القاعة: ابدأ بالأسئلة التي تتقنها، ودَع الصعب لنهاية الجولة. كل سؤال تجيبه بثقة يبني إيقاعك للذي بعده."),
        SupportMessage("exam_near-5", "exam_near", "إن واجهت سؤالًا لا تعرفه، فهو سؤال واحد من مئات. تنفّس، استبعد الخيارات المستحيلة، وامضِ — لا تدع سؤالًا يسرق امتحانًا كاملًا."),
        SupportMessage("exam_near-6", "exam_near", "أنت لا تدخل الامتحان فارغًا؛ تدخله بكل ساعة جلستها، وكل ليلة سهرتها، وكل موضوع أعدته حتى أتقنته."),
        SupportMessage("exam_near-7", "exam_near", "قرب الامتحان يعني أن النهاية صارت قريبة، لا أن الخطر صار أكبر. أنت على بُعد خطوات من حصاد تعب طويل."),
        SupportMessage("exam_near-8", "exam_near", "ثق بتحضيرك كما تثق بتشخيص بنيته على أساس متين: لقد فعلت العمل، والآن دع النتيجة تأتي."),
        SupportMessage("momentum-1", "momentum", "أنجزت اليوم أكثر مما تظن. افتح قائمة ما أنهيته واقرأها بصوت عالٍ — عقلك يحتاج أن يسمع الدليل لا أن يتذكّره."),
        SupportMessage("momentum-2", "momentum", "كل موضوع تُنهيه اليوم هو موضوع لن يخيفك غدًا. راكم هذه الانتصارات الصغيرة، فهي تصنع النتيجة الكبيرة."),
        SupportMessage("momentum-3", "momentum", "جلسة مركّزة واحدة أثمن من يوم كامل من الجلوس المتردّد. احتفل بتركيزك حين يحدث، وكرّره."),
        SupportMessage("momentum-4", "momentum", "انظر إلى أين وصلت مقارنة ببداية رحلتك: فجوات كانت مخيفة صارت مألوفة. هذا هو التقدم الحقيقي، وهو مستمر."),
        SupportMessage("momentum-5", "momentum", "ضع علامة على كل يوم تُنهي فيه خطتك. سلسلة الأيام المتصلة وقود لا يُستهان به في الأيام الصعبة."),
        SupportMessage("momentum-6", "momentum", "سؤال صعب حللته اليوم بنفسك أهم من عشرة أسئلة سهلة؛ الصعب هو الذي يرفع مستواك فعلًا."),
        SupportMessage("momentum-7", "momentum", "أنت أقرب إلى خط النهاية مما كنت عليه أمس، وستكون أقرب غدًا. استمرارك نفسه إنجاز يستحق التقدير."),
        SupportMessage("momentum-8", "momentum", "كافئ نفسك على الجهود الصغيرة: فنجان قهوة بهدوء، مشوار قصير، حديث مع صديق. من يعرف أن يرتاح يعرف أن يستمر.")
    )

    /** سطر التنويه الثابت — يظهر أسفل قسم الدعم وبطاقته دائمًا. */
    const val DISCLAIMER: String = "هذه رسائل دعم وطمأنينة من فريق وطني، وليست بديلًا عن المشورة المتخصصة. وإن زاد الضغط عن حدّ الاحتمال، فالحديث مع شخص تثق به أو مع مختص هو الخطوة الصحيحة."

    /** فهرس رسالة اليوم ضمن [all] (0..39). */
    fun dailyIndex(date: LocalDate = LocalDate.now()): Int =
        (date.toEpochDay() % all.size).toInt()

    fun dailyMessage(date: LocalDate = LocalDate.now()): SupportMessage = all[dailyIndex(date)]
}

/**
 * مفضلة رسائل الدعم: مجموعة معرّفات بـSharedPreferences
 * ("watani_support_favorites") تنعكس بـStateFlow لتتحدث كل الشاشات.
 */
object SupportFavorites {
    private const val PREFS_NAME = "watani_support_favorites"
    private const val KEY_IDS = "favorite_ids"

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _favorites.value = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        loaded = true
    }

    @Synchronized
    fun toggle(context: Context, messageId: String) {
        init(context)
        val next = if (_favorites.value.contains(messageId)) _favorites.value - messageId else _favorites.value + messageId
        _favorites.value = next
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_IDS, next).apply()
    }
}
