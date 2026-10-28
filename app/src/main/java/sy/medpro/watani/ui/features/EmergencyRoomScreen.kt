package sy.medpro.watani.ui.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.*

// ---------------------------------------------------------------------------
// Emergency Room (غرفة الطوارئ) — FROZEN DESIGN, single-player, offline.
// No AI, no network, no images, no heavy animation, no per-second simulation.
// One case is loaded at a time. Vitals change ONLY when a decision moves the
// case to the next predetermined node. Every case, every action, every
// outcome and every feedback text below is written and reviewed in advance:
// nothing is generated at runtime. Pilot set: 5 cases.
// ---------------------------------------------------------------------------

data class ErVitals(
    val hr: Int? = null,
    val sbp: Int? = null,
    val dbp: Int? = null,
    val rr: Int? = null,
    val spo2: Int? = null,
    val gcs: Int? = null
)

data class ErAction(
    val label: String,
    val points: Int,
    val feedback: String,
    /** Next node id, or null when this action ends the case. */
    val nextNodeId: String? = null,
    /** Shown when the case ends on this action. */
    val endOutcome: String? = null
)

data class ErNode(
    val id: String,
    val text: String,
    val vitals: ErVitals,
    val actions: List<ErAction>
)

data class ErCase(
    val id: String,
    val title: String,
    val presentation: String,
    val guidelineRef: String,
    val startNodeId: String,
    val nodes: List<ErNode>,
    val endSummary: String
) {
    fun node(id: String): ErNode = nodes.first { it.id == id }
}

private val erCases: List<ErCase> = listOf(
    ErCase(
        id = "stemi",
        title = "ألم صدري حاد — احتشاء بارتفاع ST",
        presentation = "رجل 58 سنة، ألم صدري ضاغط خلف القص منذ ساعتين ينتشر للذراع اليسرى، مع تعرق وغثيان. لا قصة نزف حديث ولا جراحة. ضغطه 150/90، ولا علامات قصور قلب.",
        guidelineRef = "ESC 2023 ACS Guidelines: ECG خلال 10 دقائق، أسبرين، وإعادة تروية فورية (PCI أولي إن أمكن خلال 120 دقيقة).",
        startNodeId = "s1",
        nodes = listOf(
            ErNode(
                id = "s1",
                text = "وصل المريض للطوارئ قبل 5 دقائق. ما أول ما تفعله؟",
                vitals = ErVitals(hr = 98, sbp = 150, dbp = 90, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تخطيط قلب 12 مسرى فورًا + أسبرين 300 ملغ + مراقبة قلبية",
                        points = 10,
                        feedback = "صحيح. تخطيط القلب خلال 10 دقائق هو ما يثبت STEMI ويفتح باب إعادة التروية، والأسبرين يقلل الوفيات.",
                        nextNodeId = "s2"
                    ),
                    ErAction(
                        label = "إعطاء حالّ للخثرة فورًا قبل التخطيط",
                        points = -5,
                        feedback = "خطأ. إعادة التروية لا تُعطى قبل إثبات STEMI بالتخطيط؛ التشخيص الخاطئ قد يعرّض المريض لنزف بلا فائدة.",
                        nextNodeId = "s2"
                    ),
                    ErAction(
                        label = "صورة صدر أولًا ثم انتظار التروبونين",
                        points = -5,
                        feedback = "خطأ. الصورة والتروبونين يؤخران إعادة التروية؛ في STEMI القرار بالتخطيط لا بانتظار الواسمات.",
                        nextNodeId = "s2"
                    ),
                    ErAction(
                        label = "تخريج المريض بمضاد حموضة لاشتباه حرقة",
                        points = -10,
                        feedback = "خطير. ألم صدري ضاغط مع تعرق عند رجل 58 سنة لا يُفسَّر بالحرقة قبل نفي الاحتشاء.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة مبكرًا: المريض خُرّج بلا تقييم وتدهور لاحقًا. راجع التغذية الراجعة وأعد الحالة."
                    )
                )
            ),
            ErNode(
                id = "s2",
                text = "التخطيط: ارتفاع ST في V2-V4 مع أمواج T شاهقة — STEMI أمامي حاد. مركز القثطرة متاح خلال 60 دقيقة. ما تدبيرك؟",
                vitals = ErVitals(hr = 104, sbp = 145, dbp = 88, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تحويل فوري لقثطرة أولية (Primary PCI)",
                        points = 10,
                        feedback = "صحيح. PCI الأولي هو الخيار الأول عندما يكون متاحًا خلال 120 دقيقة من التشخيص.",
                        nextNodeId = "s3"
                    ),
                    ErAction(
                        label = "حالّ للخثرة رغم توفر القثطرة خلال ساعة",
                        points = -5,
                        feedback = "غير مثالي. الحالّ خيار عندما تتأخر القثطرة أكثر من 120 دقيقة، أما هنا فـPCI متفوق.",
                        nextNodeId = "s3"
                    ),
                    ErAction(
                        label = "انتظار نتيجة التروبونين قبل أي إعادة تروية",
                        points = -5,
                        feedback = "خطأ. في STEMI لا ننتظر الواسمات؛ كل دقيقة تأخير تموت فيها عضلة قلبية.",
                        nextNodeId = "s3"
                    )
                )
            ),
            ErNode(
                id = "s3",
                text = "تمت القثطرة بنجاح وفتح الشريان. المريض مستقر الآن. ما الخطة بعد إعادة التروية؟",
                vitals = ErVitals(hr = 82, sbp = 128, dbp = 78, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "مضادا صفيحات (أسبرين + مثبط P2Y12) + ستاتين عالي الشدة + مراقبة",
                        points = 10,
                        feedback = "صحيح. العلاج المضاد للصفيحات المزدوج والستاتين عالي الشدة هما أساس ما بعد PCI.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: إعادة تروية ناجحة وخطة ثانوية صحيحة."
                    ),
                    ErAction(
                        label = "إيقاف الأسبرين لتجنب النزف",
                        points = -10,
                        feedback = "خطأ خطير. إيقاف مضاد الصفيحات بعد الدعامة يرفع خطر خثار الدعامة والاحتشاء المتكرر.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخطأ علاجي: لا يُوقف مضاد الصفيحات بعد الدعامة."
                    ),
                    ErAction(
                        label = "تخريج فوري بلا مراقبة ولا أدوية",
                        points = -5,
                        feedback = "خطأ. مريض ما بعد STEMI يحتاج مراقبة وعلاجًا ثانويًا وقائيًا قبل التخريج.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخطة ناقصة: المراقبة والوقاية الثانوية إلزاميتان."
                    )
                )
            )
        ),
        endSummary = "الدرس: في STEMI، التخطيط خلال 10 دقائق، ثم إعادة تروية فورية، ثم وقاية ثانوية — بلا انتظار للواسمات."
    ),
    ErCase(
        id = "anaphylaxis",
        title = "صدمة تأقية بعد لسعة نحل",
        presentation = "امرأة 34 سنة، بعد لسعة نحل بدقائق: شرى منتشر، تورم شفاه، صرير، ضيق نفس، ودوخة. لا قصة ربو شديد سابق.",
        guidelineRef = "Resuscitation Council / NICE: أدرينالين عضلي 0.5 ملغ فورًا هو العلاج الأول والمنقذ للحياة في التأق.",
        startNodeId = "a1",
        nodes = listOf(
            ErNode(
                id = "a1",
                text = "المريضة تتدهور: صرير يزداد وضغطها يهبط. ما الإجراء الأول؟",
                vitals = ErVitals(hr = 122, sbp = 82, dbp = 50, rr = 28, spo2 = 90, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "أدرينالين 0.5 ملغ عضلي في منتصف الفخذ الخارجي فورًا",
                        points = 10,
                        feedback = "صحيح ومنقذ للحياة. الأدرينالين العضلي هو العلاج الأول في التأق ولا يُؤجَّل لأي دواء آخر.",
                        nextNodeId = "a2"
                    ),
                    ErAction(
                        label = "مضاد هستامين وريدي فقط ومراقبة",
                        points = -10,
                        feedback = "خطأ خطير. مضاد الهستامين لا يعالج انسداد الطريق الهوائي ولا الصدمة، وتأخير الأدرينالين قاتل.",
                        nextNodeId = "a2"
                    ),
                    ErAction(
                        label = "ستيرويد فموي وانتظار التحسن",
                        points = -10,
                        feedback = "خطأ. الستيرويد بطيء ولا يوقف التأق الحاد؛ لا مكان له كعلاج أول.",
                        nextNodeId = "a2"
                    ),
                    ErAction(
                        label = "أدرينالين وريدي جرعة كبيرة فورًا",
                        points = -5,
                        feedback = "غير صحيح كخطوة أولى خارج العناية: الجرعة الوريدية الكبيرة تسبب اضطراب نظم؛ الطريق العضلي هو الآمن والموصى به.",
                        nextNodeId = "a2"
                    )
                )
            ),
            ErNode(
                id = "a2",
                text = "تحسنت المريضة جزئيًا بعد الأدرينالين: الصرير خفّ والضغط 105/65. ما الخطوة التالية؟",
                vitals = ErVitals(hr = 108, sbp = 105, dbp = 65, rr = 22, spo2 = 94, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أكسجين + سوائل وريدية + مراقبة 6-12 ساعة مع جرعة أدرينالين ثانية عند الحاجة",
                        points = 10,
                        feedback = "صحيح. التأق قد يعود بطور ثانٍ (biphasic)؛ المراقبة لساعات والاستعداد لجرعة ثانية إلزاميان.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تأق عولج بالأدرينالين الفوري ثم مراقبة كاملة."
                    ),
                    ErAction(
                        label = "تخريج فوري لأنها تحسنت",
                        points = -10,
                        feedback = "خطأ خطير. الطور الثاني للتأق قد يعود بعد ساعات؛ التخريج الفوري يعرضها لخطر الوفاة خارج المستشفى.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخطأ: التخريج المبكر بعد التأق مرفوض."
                    ),
                    ErAction(
                        label = "إعطاء مهدئ للنوم ومراقبة خفيفة",
                        points = -5,
                        feedback = "خطأ. التهدئة تخفي تدهور الطريق الهوائي؛ المطلوب مراقبة يقظة وأكسجين وسوائل.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخطة ناقصة: التهدئة ليست علاجًا للتأق."
                    )
                )
            )
        ),
        endSummary = "الدرس: في التأق، الأدرينالين العضلي فورًا، ثم أكسجين وسوائل ومراقبة طويلة — مضاد الهستامين والستيرويد ليسا علاجًا أولًا."
    ),
    ErCase(
        id = "sepsis",
        title = "صدمة إنتانية عند مسنّ",
        presentation = "رجل 76 سنة، حرارة 39.2، ارتباك جديد، ضغط 88/55، بول قليل، ولاكتات 4.2 mmol/L. مصدر محتمل: التهاب بول.",
        guidelineRef = "Surviving Sepsis Campaign: مزارع + صادات واسعة خلال ساعة، و30 مل/كغ بلوريات، ونورأدرينالين عند MAP أقل من 65 بعد السوائل.",
        startNodeId = "p1",
        nodes = listOf(
            ErNode(
                id = "p1",
                text = "المريض بعلامات صدمة إنتانية. ما حزمة الساعة الأولى؟",
                vitals = ErVitals(hr = 118, sbp = 88, dbp = 55, rr = 24, spo2 = 93, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "مزارع دم وبول + صادات واسعة خلال ساعة + 30 مل/كغ سوائل بلورية",
                        points = 10,
                        feedback = "صحيح. حزمة الساعة الأولى: لا تؤخر الصادات بانتظار المزارع، ولا تؤخر السوائل.",
                        nextNodeId = "p2"
                    ),
                    ErAction(
                        label = "انتظار نتائج المزارع قبل إعطاء الصادات",
                        points = -10,
                        feedback = "خطأ. كل ساعة تأخير بالصادات في الصدمة الإنتانية ترفع الوفيات؛ المزارع تُؤخذ أولًا لكن لا تُنتظر.",
                        nextNodeId = "p2"
                    ),
                    ErAction(
                        label = "سوائل فقط بلا صادات",
                        points = -5,
                        feedback = "ناقص. السوائل وحدها لا تعالج مصدر الإنتان؛ الصادات المبكرة ركن أساسي.",
                        nextNodeId = "p2"
                    ),
                    ErAction(
                        label = "خافض حرارة فقط ومراقبة",
                        points = -10,
                        feedback = "خطأ. خافض الحرارة لا يعالج الصدمة الإنتانية ولا يغني عن الصادات والسوائل.",
                        nextNodeId = "p2"
                    )
                )
            ),
            ErNode(
                id = "p2",
                text = "بعد 30 مل/كغ سوائل، الضغط ما زال 88/52 (MAP ≈ 64) والبول ما زال قليلًا. ما التالي؟",
                vitals = ErVitals(hr = 112, sbp = 88, dbp = 52, rr = 22, spo2 = 94, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "نورأدرينالين وريدي لرفع MAP إلى 65 فأعلى + قبول عناية",
                        points = 10,
                        feedback = "صحيح. النورأدرينالين هو مقبض الأوعية الأول عندما يبقى MAP أقل من 65 بعد السوائل.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: حزمة إنتان كاملة ثم مقبض أوعية عند الحاجة."
                    ),
                    ErAction(
                        label = "دوبامين كخيار أول",
                        points = -5,
                        feedback = "غير مفضل. الدوبامين يسبب اضطراب نظم أكثر من النورأدرينالين ولا يُستخدم أولًا في الصدمة الإنتانية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخيار غير مفضل: النورأدرينالين أولًا."
                    ),
                    ErAction(
                        label = "المزيد من السوائل بلا نهاية وبلا مقبض أوعية",
                        points = -5,
                        feedback = "خطأ. الإفراط بالسوائل بعد عدم الاستجابة يسبب وذمة رئوية؛ عندها يُضاف مقبض الأوعية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإفراط سوائل: المقبض الوعائي مطلوب عند MAP المنخفض المستمر."
                    )
                )
            )
        ),
        endSummary = "الدرس: في الإنتان، مزارع ثم صادات خلال ساعة وسوائل كافية، ثم نورأدرينالين عند استمرار انخفاض MAP."
    ),
    ErCase(
        id = "asthma",
        title = "ربو حاد شديد عند شاب",
        presentation = "شاب 22 سنة، ربو معروف، نوبة منذ 6 ساعات: لا يستطيع إكمال جملة، صدر صامت نسبيًا، إعياء واضح، SpO2 ‏88% على الهواء.",
        guidelineRef = "GINA: أكسجين بهدف 94-98%، SABA + إبراتروبيوم بالرذاذ، ستيرويد جهازي، وكبريتات المغنيسيوم الوريدية عند الشدة غير المستجيبة.",
        startNodeId = "h1",
        nodes = listOf(
            ErNode(
                id = "h1",
                text = "المريض بضائقة تنفسية شديدة وصدر شبه صامت. ما العلاج الفوري؟",
                vitals = ErVitals(hr = 128, sbp = 132, dbp = 80, rr = 32, spo2 = 88, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أكسجين (هدف 94-98%) + سالبوتامول وإبراتروبيوم بالرذاذ + ستيرويد جهازي",
                        points = 10,
                        feedback = "صحيح. هذه الثلاثية هي أساس علاج الربو الحاد الشديد، مع مراقبة مستمرة.",
                        nextNodeId = "h2"
                    ),
                    ErAction(
                        label = "صادات وريدية فقط",
                        points = -5,
                        feedback = "خطأ. الصادات لا تعالج التشنج القصبي؛ الربو الحاد علاجه موسعات وستيرويد.",
                        nextNodeId = "h2"
                    ),
                    ErAction(
                        label = "تهدئة قوية ليرتاح المريض",
                        points = -10,
                        feedback = "خطير. التهدئة في ضائقة تنفسية شديدة تخفي التدهور وتسرّع توقف التنفس.",
                        nextNodeId = "h2"
                    ),
                    ErAction(
                        label = "تنبيب فوري قبل أي علاج دوائي",
                        points = -5,
                        feedback = "غير صحيح كخطوة أولى. التنبيب يُحفظ للإعياء أو توقف التنفس أو فشل العلاج الطبي، وهو نفسه محفوف بالخطر في الربو.",
                        nextNodeId = "h2"
                    )
                )
            ),
            ErNode(
                id = "h2",
                text = "بعد الرذاذ والستيرويد، التحسن جزئي: SpO2 ‏91% وما زال بضائقة. ما الإضافة التالية؟",
                vitals = ErVitals(hr = 120, sbp = 128, dbp = 78, rr = 28, spo2 = 91, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "كبريتات المغنيسيوم وريدي + استمرار الرذاذ ومراقبة لصيقة",
                        points = 10,
                        feedback = "صحيح. المغنيسيوم الوريدي مفيد في الربو الشديد غير المستجيب، مع استمرار العلاج الأساسي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: علاج ربو حاد متدرج حتى الاستجابة."
                    ),
                    ErAction(
                        label = "تخريج المريض لأن التحسن جزئي موجود",
                        points = -10,
                        feedback = "خطأ. التحسن الجزئي مع SpO2 منخفض وضائقة مستمرة ليس معيار تخريج آمن.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج غير آمن."
                    ),
                    ErAction(
                        label = "إيقاف كل العلاج والانتظار",
                        points = -10,
                        feedback = "خطأ. إيقاف العلاج في ربو شديد يعيد التدهور سريعًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف علاج ضروري."
                    )
                )
            )
        ),
        endSummary = "الدرس: في الربو الحاد، أكسجين وموسعات وستيرويد فورًا، ثم مغنيسيوم عند عدم الاستجابة — ولا تهدئة ولا تخريج مبكر."
    ),
    ErCase(
        id = "hypo",
        title = "نقص سكر مع غياب وعي جزئي",
        presentation = "رجل 64 سنة، سكري على أنسولين، وُجد مرتبكًا ومتعرقًا، لا يستطيع البلع بأمان. سكر الدم الشعري 38 ملغ/دل. لا علامات إنتان ولا سكتة واضحة.",
        guidelineRef = "ADA Standards of Care: المريض فاقد الوعي أو غير القادر على البلع لا يُعطى شيئًا بالفم (خطر الشفط) — دكستروز وريدي أو غلوكاغون عضلي.",
        startNodeId = "g1",
        nodes = listOf(
            ErNode(
                id = "g1",
                text = "سكر الدم 38 والمريض لا يبلع بأمان. ما الإجراء الفوري؟",
                vitals = ErVitals(hr = 104, sbp = 138, dbp = 82, rr = 18, spo2 = 97, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "دكستروز وريدي (أو غلوكاغون عضلي إن تعذر الوريد) ولا شيء بالفم",
                        points = 10,
                        feedback = "صحيح. في نقص الوعي أو عدم القدرة على البلع، الفم ممنوع (شفط) والعلاج وريدي أو غلوكاغون.",
                        nextNodeId = "g2"
                    ),
                    ErAction(
                        label = "عصير أو سكر بالفم رغم عدم القدرة على البلع",
                        points = -10,
                        feedback = "خطير. إعطاء سوائل بالفم لمريض لا يبلع بأمان يسبب شفطًا والتهاب رئة شفطي.",
                        nextNodeId = "g2"
                    ),
                    ErAction(
                        label = "جرعة أنسولين لتصحيح السكر",
                        points = -10,
                        feedback = "خطأ قاتل. الأنسولين يعمّق نقص السكر؛ المريض يحتاج غلوكوز لا أنسولين.",
                        nextNodeId = "g2"
                    ),
                    ErAction(
                        label = "انتظار استيقاظه من تلقاء نفسه",
                        points = -10,
                        feedback = "خطأ. نقص السكر الشديد المطوّل يسبب أذية دماغية؛ العلاج فوري.",
                        nextNodeId = "g2"
                    )
                )
            ),
            ErNode(
                id = "g2",
                text = "استعاد المريض وعيه والسكر الآن 112 ملغ/دل. ما الخطوة التالية قبل التخريج؟",
                vitals = ErVitals(hr = 88, sbp = 126, dbp = 76, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "كربوهيدرات طويلة المفعول + مراجعة سبب النقص (جرعة أنسولين، طعام، كحول) وتثقيف",
                        points = 10,
                        feedback = "صحيح. بعد رفع السكر سريعًا، الكربوهيدرات الطويلة تمنع النكس، ومراجعة السبب تمنع التكرار.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: إنقاذ من نقص سكر ثم وقاية من النكس."
                    ),
                    ErAction(
                        label = "تخريج فوري بلا طعام ولا مراجعة سبب",
                        points = -5,
                        feedback = "ناقص. نقص السكر قد ينكس، خاصة مع الأنسولين طويل المفعول؛ لا تخريج بلا وقاية ومراجعة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج ناقص: الوقاية من النكس إلزامية."
                    )
                )
            )
        ),
        endSummary = "الدرس: نقص السكر مع عدم القدرة على البلع = لا شيء بالفم، دكستروز أو غلوكاغون، ثم كربوهيدرات طويلة ومراجعة السبب."
    )
)

// ---------------------------------------------------------------------------
// UI state: one case at a time, one node at a time.
// ---------------------------------------------------------------------------

private data class ErMistake(val caseTitle: String, val actionLabel: String, val feedback: String)

@Composable
fun EmergencyRoomScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var caseIndex by remember { mutableIntStateOf(-1) }
    var nodeId by remember { mutableStateOf<String?>(null) }
    var totalScore by remember { mutableIntStateOf(0) }
    var caseScore by remember { mutableIntStateOf(0) }
    var pendingFeedback by remember { mutableStateOf<ErAction?>(null) }
    var pendingNextNode by remember { mutableStateOf<String?>(null) }
    var pendingOutcome by remember { mutableStateOf<String?>(null) }
    var caseEnded by remember { mutableStateOf(false) }
    var shiftDone by remember { mutableStateOf(false) }
    var mistakes by remember { mutableStateOf<List<ErMistake>>(emptyList()) }
    var caseScores by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    fun startCase(index: Int) {
        caseIndex = index
        nodeId = erCases[index].startNodeId
        caseScore = 0
        pendingFeedback = null
        pendingNextNode = null
        pendingOutcome = null
        caseEnded = false
    }

    DrawerScreenScaffold(title = "غرفة الطوارئ (مناوبة فردية)", onBack = onBack) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                shiftDone -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaroonDeep), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("تقرير المناوبة", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("مجموع نقاط المناوبة: $totalScore", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("الحالات المنجزة: ${caseScores.size} من ${erCases.size}", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Text("نتائج الحالات:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                    caseScores.forEach { (title, score) ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("$score نقطة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    if (mistakes.isNotEmpty()) {
                        Text("مراجعة الأخطاء:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                        mistakes.forEach { m ->
                            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(m.caseTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("اخترت: ${m.actionLabel}", fontSize = 12.sp)
                                    Text(m.feedback, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Text("بلا أخطاء — مناوبة نظيفة تمامًا.", fontSize = 13.sp, color = MaroonPrimary, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            caseIndex = -1; totalScore = 0; caseScores = emptyList()
                            mistakes = emptyList(); shiftDone = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إعادة المناوبة") }
                }

                caseIndex < 0 -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaroonDeep), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("غرفة الطوارئ — مناوبة فردية أوفلاين", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("5 حالات تجريبية. كل حالة: عرض وعلامات حيوية، ثم اختر إجراءً من 3-4 إجراءات. العلامات تتغير فقط مع قرارك، والتغذية الراجعة تشرح لماذا الصح صحيح ولماذا الخطأ خطأ، مع مرجع إرشادي. بلا اتصال أثناء اللعب.", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    erCases.forEachIndexed { index, c ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${index + 1}. ${c.title}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
                                Text(c.presentation, fontSize = 12.sp)
                                Button(
                                    onClick = { startCase(index) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                                ) { Text("ابدأ هذه الحالة") }
                            }
                        }
                    }
                    Button(
                        onClick = { startCase(0) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ابدأ المناوبة كاملة (5 حالات)", color = Color.Black, fontWeight = FontWeight.Bold) }
                }

                else -> {
                    val c = erCases[caseIndex]
                    val node = c.node(nodeId ?: c.startNodeId)
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaroonPrimary.copy(alpha = 0.1f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("الحالة ${caseIndex + 1} من ${erCases.size}: ${c.title}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaroonPrimary)
                            Text(c.presentation, fontSize = 13.sp)
                            Text("المرجع: ${c.guidelineRef}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    VitalsCard(node.vitals)
                    Text("نقاط هذه الحالة: $caseScore | مجموع المناوبة: $totalScore", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaroonPrimary)

                    if (caseEnded) {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaroonDeep)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(pendingOutcome ?: c.endSummary, color = Color.White, fontSize = 13.sp)
                                Text(c.endSummary, color = GoldAccent, fontSize = 12.sp)
                                Text("نقاط هذه الحالة: $caseScore", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = {
                                caseScores = caseScores + (c.title to caseScore)
                                if (caseIndex + 1 < erCases.size) startCase(caseIndex + 1) else shiftDone = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (caseIndex + 1 < erCases.size) "الحالة التالية" else "تقرير المناوبة") }
                    } else if (pendingFeedback != null) {
                        val fb = pendingFeedback!!
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (fb.points >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (fb.points >= 0) "إجراء صحيح (+${fb.points})" else "إجراء خاطئ (${fb.points})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(fb.feedback, fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = {
                                if (pendingNextNode == null) {
                                    caseEnded = true
                                } else {
                                    nodeId = pendingNextNode
                                    pendingFeedback = null
                                    pendingNextNode = null
                                    pendingOutcome = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (pendingNextNode == null) "إنهاء الحالة" else "متابعة") }
                    } else {
                        Text(node.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        node.actions.forEach { action ->
                            Button(
                                onClick = {
                                    caseScore += action.points
                                    totalScore += action.points
                                    if (action.points < 0) {
                                        mistakes = mistakes + ErMistake(c.title, action.label, action.feedback)
                                    }
                                    pendingFeedback = action
                                    pendingNextNode = action.nextNodeId
                                    pendingOutcome = action.endOutcome
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(action.label, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalsCard(v: ErVitals) {
    fun fmt(value: Int?, suffix: String): String = if (value == null) "—" else "$value$suffix"
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MonitorHeart, null, tint = MaroonPrimary)
                Text("العلامات الحيوية", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaroonPrimary)
            }
            Text("النبض: ${fmt(v.hr, "")} /د  •  الضغط: ${fmt(v.sbp, "")}/${fmt(v.dbp, "")}  •  التنفس: ${fmt(v.rr, "")} /د", fontSize = 13.sp)
            Text("الأكسجة SpO2: ${fmt(v.spo2, "%")}  •  غلاسكو GCS: ${fmt(v.gcs, "/15")}", fontSize = 13.sp)
        }
    }
}
