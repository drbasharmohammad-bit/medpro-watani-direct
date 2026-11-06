package sy.medpro.watani.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** رسالة دعم نفسي واحدة من بنك الدعم v2 (40 رسالة، 8 لكل تصنيف). */
data class SupportMessage(
    val id: String,
    val categoryId: String,
    val text: String
)

data class SupportCategory(val id: String, val title: String)

/**
 * بنك رسائل الدعم والطمأنينة v2 — النسخة الوحيدة المعتمدة (مراجعة
 * المشرف الخبير، 6 نوفمبر 2026): النصوص حرفية من مواصفة التأليف
 * (build-checks/support-bank-v1.md بنسختها v2) بلا أي تعديل.
 * الدوران اليومي: index = LocalDate.now().toEpochDay() % 40 على
 * قائمة [all] بترتيب التصنيفات أدناه.
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
        SupportMessage("exhaustion-1", "exhaustion", "التعب الذي تشعر به الآن ليس علامة ضعف؛ إنه إثبات أنك بذلت جهدًا حقيقيًا. خذ استراحة قصيرة، فالعقل المُجهد يصعب عليه التثبيت، وقسط من الراحة يعيد لذهنك صفاءه وقدرته على الحفظ."),
        SupportMessage("exhaustion-2", "exhaustion", "لا يُطلب منك أن تدرس وأنت منهك. نم جيدًا الليلة؛ فما يصعب تثبيته اليوم بنصف تركيز، غالبًا سيثبت أسرع غدًا بعقل مرتاح."),
        SupportMessage("exhaustion-3", "exhaustion", "حتى الأطباء الكبار الذين تراهم اليوم عرفوا أيامًا من التعب الكامل، ونهايات أيام لم يطيقوا فيها صفحة أخرى. ما تشعر به جزء من الطريق لا علامة خروج عنه؛ ارتح اليوم، وستكمل كما أكملوا."),
        SupportMessage("exhaustion-4", "exhaustion", "جسدك شريكك في هذا الامتحان لا خصمك: كوب ماء، وقفة قصيرة، ونَفَس عميق… ثم عودة هادئة إلى المكتب."),
        SupportMessage("exhaustion-5", "exhaustion", "إذا شعرت أن رأسك امتلأ، فهذا لا يعني أنك نسيت؛ الذهن المشبَع يحتاج فترة هدوء ليرتّب ما أخذه، وكثير من التثبيت يحدث حين تبتعد قليلًا عن الكتاب."),
        SupportMessage("exhaustion-6", "exhaustion", "يوم خفيف لا يهدم شهورًا من العمل. اسمح لنفسك بإيقاع أبطأ اليوم، وستعوّض غدًا بطاقة أصفى."),
        SupportMessage("exhaustion-7", "exhaustion", "أنت لا تحتاج إلى طاقة خارقة، تحتاج فقط إلى أن تنام وتأكل وتتحرك قليلًا. الأساسيات الصغيرة هي التي تحمل المشاريع الكبيرة."),
        SupportMessage("exhaustion-8", "exhaustion", "أغلق الكتاب حين تجد نفسك تعيد قراءة السطور نفسها دون أن يتقدّم فهمك. التوقف المؤقت قرار ذكي، لا استسلام."),
        SupportMessage("anxiety-1", "anxiety", "القلق قبل الامتحان شعور طبيعي يمر به كل مجتهد؛ إنه يعني أن الأمر يهمّك، لا أنك غير جاهز."),
        SupportMessage("anxiety-2", "anxiety", "لا تحاول أن تهدّئ كل أفكارك دفعة واحدة. ركّز على الساعة القادمة فقط: صفحة واحدة، موضوع واحد، خطوة واحدة."),
        SupportMessage("anxiety-3", "anxiety", "تذكّر: الامتحان يقيس ما تعرفه في يوم واحد، ولا يلخّص قيمتك كطبيب ولا سنوات تعبك كلها."),
        SupportMessage("anxiety-4", "anxiety", "عندما يشتد التوتر، عد إلى ما أنجزته فعلًا: مواضيع أنهيتها، أسئلة أتقنتها، أيام صعبة تجاوزتها. سجلّك أقوى من خوفك."),
        SupportMessage("anxiety-5", "anxiety", "حين يشتد التوتر، جرّب أن تبطئ تنفّسك قليلًا وتمنح نفسك دقيقة هدوء قبل أن تكمل. خطوات صغيرة كهذه قد تخفف حدّة اللحظة وتعيدك إلى ما بين يديك."),
        SupportMessage("anxiety-6", "anxiety", "حين تسمع أن زميلك أنهى المادة مرتين، تذكّر أنك لا ترى إلا النتيجة. أنت تعرف طريقك: المواضيع التي أغلقتها، والأسئلة التي صارت أسهل عليك من قبل. قارن تقدّمك بما كنت عليه قبل شهر، فتلك هي المقارنة الأعدل."),
        SupportMessage("anxiety-7", "anxiety", "إن مررت بسؤال لا تعرفه أثناء المراجعة، فهذا خبر جيد: وجدت ثغرة اليوم، لا يوم الامتحان."),
        SupportMessage("anxiety-8", "anxiety", "الخوف من أن تنسى كل شيء شعور يزور كل طالب مجتهد. لكن النسيان المؤقت في لحظة توتر لا يعني أن المعلومة غابت؛ كثير مما راجعته بتركيز يعود إلى ذهنك حين تهدأ وتحتاجه، والمراجعة الهادئة اليوم تقوّي هذا الأثر."),
        SupportMessage("self_doubt-1", "self_doubt", "شكّك في نفسك لا يعني أنك ضعيف؛ أكثر الطلاب اجتهادًا هم أكثرهم مراجعة لأنفسهم. الفرق أنهم يكملون رغم الشك."),
        SupportMessage("self_doubt-2", "self_doubt", "لست مطالبًا بأن تعرف كل شيء. أنت مطالب بأن تعرف ما يكفي لتنجح وتتقدّم — وهذا ما تبنيه كل يوم."),
        SupportMessage("self_doubt-3", "self_doubt", "وجودك هنا، في هذه المرحلة من الطريق، لم يأتِ صدفة؛ إنه حصيلة سنوات من الاجتياز والتقدّم. ما تبقّى صعب أيضًا، لكنك تملك سجلًا حقيقيًا من الصعوبات التي تجاوزتها فعلًا، وهذا السجل دليلك لا حدسك القلق."),
        SupportMessage("self_doubt-4", "self_doubt", "أخطاؤك في التدريب ليست حكمًا عليك؛ إنها الخريطة التي ترشدك إلى ما يحتاج تقوية. كل طبيب ناجح مرّ من هنا."),
        SupportMessage("self_doubt-5", "self_doubt", "لا تقس يومًا سيئًا بمقياس مسيرتك كلها. يوم واحد لا يمحو شهورًا، وسؤال واحد لا يلغي مئات الأسئلة التي أجبتها صوابًا."),
        SupportMessage("self_doubt-6", "self_doubt", "في الأيام الثقيلة، انظر إلى ما ينتظرك بعد هذا الامتحان: سنوات التخصص التي اخترت طريقها، والمرضى الذين ستقف أمامهم يومًا بثقة. تعب هذا الموسم مؤقت، وما يبنيه فيك يبقى."),
        SupportMessage("self_doubt-7", "self_doubt", "الثقة لا تأتي قبل العمل؛ تأتي بعده. كل جلسة تنهيها، صغيرة كانت أم كبيرة، تضيف لبنة إلى ثقتك بنفسك."),
        SupportMessage("self_doubt-8", "self_doubt", "إن شعرت أن الجميع يتقدّم أسرع منك، فتذكّر أنك ترى نتائجهم الظاهرة ولا ترى لياليهم الصعبة."),
        SupportMessage("exam_near-1", "exam_near", "في الأيام الأخيرة، الهدوء لا يقل أهمية عن أي معلومة جديدة. راجع ما تعرفه وثبّته؛ فمن يدخل القاعة هادئًا يقرأ الأسئلة بوضوح ويستخرج ما يعرفه فعلًا."),
        SupportMessage("exam_near-2", "exam_near", "لا تفتح مواضيع جديدة كليًا في اللحظات الأخيرة؛ ما بنيته طوال الشهور هو رصيدك الحقيقي، ومراجعته بهدوء هي الاختيار الأرشد لهذه الأيام."),
        SupportMessage("exam_near-3", "exam_near", "جهّز أغراضك وخطتك لليوم الكبير من الليلة السابقة، ونم مبكرًا. عقل مرتاح صباح الامتحان يساوي ساعات مراجعة إضافية."),
        SupportMessage("exam_near-4", "exam_near", "في القاعة: ابدأ بالأسئلة التي تتقنها، ودَع الصعب لنهاية الجولة. كل سؤال تجيبه بثقة يبني إيقاعك للذي بعده."),
        SupportMessage("exam_near-5", "exam_near", "إن واجهت سؤالًا لا تعرفه، فهو سؤال واحد من مئات. تنفّس، استبعد الخيارات المستحيلة، وامضِ — لا تدع سؤالًا يسرق امتحانًا كاملًا."),
        SupportMessage("exam_near-6", "exam_near", "أنت لا تدخل الامتحان فارغًا؛ تدخله بكل ساعة جلستها للدراسة، وكل موضوع أعدته حتى أتقنته، وكل يوم اخترت فيه أن تستمر. هذا الرصيد كله معك في القاعة."),
        SupportMessage("exam_near-7", "exam_near", "قرب الامتحان يعني أن النهاية صارت قريبة، لا أن الخطر صار أكبر. أنت على بُعد خطوات من حصاد تعب طويل."),
        SupportMessage("exam_near-8", "exam_near", "لقد فعلت الجزء الأصعب: جلست ودرست وراجعت يومًا بعد يوم. يوم الامتحان لا يطلب منك أكثر من أن تحضر بما بنيته، فثق بعملك ودَع النتيجة تأتي في وقتها."),
        SupportMessage("momentum-1", "momentum", "قبل أن تنام، مرّر في ذهنك ما أنجزته اليوم مهما كان صغيرًا: صفحات قرأتها، أسئلة فهمتها، نصف ساعة من التركيز الحقيقي. ملاحظة التقدّم بهدوء، دون مقارنة بأحد، وقود هادئ لليوم التالي."),
        SupportMessage("momentum-2", "momentum", "كل موضوع تُنهيه اليوم غالبًا لن يخيفك غدًا كما خفت منه أول مرة؛ الإلمام يزيل نصف الخوف. راكم هذه الانتصارات الصغيرة، فهي تصنع النتيجة الكبيرة."),
        SupportMessage("momentum-3", "momentum", "جلسة مركّزة واحدة أثمن من يوم كامل من الجلوس المتردّد. احتفل بتركيزك حين يحدث، وكرّره."),
        SupportMessage("momentum-4", "momentum", "انظر إلى أين وصلت مقارنة ببداية رحلتك: فجوات كانت مخيفة صارت مألوفة. هذا هو التقدم الحقيقي، وهو مستمر."),
        SupportMessage("momentum-5", "momentum", "ضع علامة على كل يوم تُنهي فيه خطتك، واستمتع برؤية الأيام تتجاور. وإن انقطع التتابع يومًا، فما بنيته قبله لا يُمحى؛ والعودة بعد توقّف هي نفسها علامة قوة."),
        SupportMessage("momentum-6", "momentum", "حين تحلّ سؤالًا صعبًا بنفسك بعد جهد، فأنت تبني مهارة لا يمنحها السهل: تحليل المعطيات وربطها تحت الضغط. والأسئلة السهلة أيضًا رصيدك — ثباتك عليها هو ما يحوّل المعرفة إلى علامات."),
        SupportMessage("momentum-7", "momentum", "أنت أقرب إلى خط النهاية مما كنت عليه أمس، وستكون أقرب غدًا. استمرارك نفسه إنجاز يستحق التقدير."),
        SupportMessage("momentum-8", "momentum", "حين تُنهي هدفًا وضعته لنفسك، كافئ نفسك بلا تردد: حديث قصير مع صديق، أو وقت مع أهلك، أو مشوار في الهواء. المكافآت الصغيرة المشروطة بالإنجاز تجعل الاستمرار عادة لا معركة يومية.")
    )

    /** سطر التنويه الثابت — تذييل دائم أسفل شاشة الدعم وبطاقته. */
    const val DISCLAIMER: String = "هذه رسائل دعم وطمأنينة من فريق وطني، وليست بديلًا عن المشورة المتخصصة. وإن لاحظت أن الضغط صار مستمرًا ويؤثّر على نومك ودراستك ويومك، فلا تنتظر أن يشتدّ أكثر: تحدّث مع شخص تثق به، أو راجع خدمات الدعم والإرشاد في جامعتك أو عيادات الطب النفسي في المشافي الجامعية — طلب المساعدة خطوة قوة لا ضعف."

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
