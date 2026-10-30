package sy.medpro.watani.ui.features

// Wave A — cardiac & vascular emergencies (50 cases).
// Uses ErVitals / ErAction / ErNode / ErCase defined in EmergencyRoomScreen.kt — not redefined here.
// Frozen rules: offline, fixed pre-reviewed bank, vitals change only between nodes, no AI, no images.
// Scoring per node: exactly one +10 action; others -5 (suboptimal) or -10 (dangerous).
// Terminal actions carry endOutcome; non-terminal actions carry nextNodeId.

val erCasesWaveA: List<ErCase> = listOf(
    ErCase(
        id = "a_nstemi",
        title = "متلازمة إكليلية حادة بلا ارتفاع ST — NSTEMI",
        presentation = "رجل 66 سنة، ألم صدري متقطع منذ 6 ساعات يزداد بالجهد، مع تعرق خفيف. التخطيط: انخفاض ST في V5-V6، والتروبونين مرتفع. الضغط 150/90 ولا قصور قلب ظاهر.",
        guidelineRef = "ESC 2023 ACS Guidelines: أسبرين تحميلي 150-300 ملغ + مضاد P2Y12 + مضاد تخثر، وقثطرة مبكرة خلال 24 ساعة عند الخطر العالي.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "التشخيص NSTEMI عالي الخطر. ما حزمة العلاج الأولى؟",
                vitals = ErVitals(hr = 92, sbp = 150, dbp = 90, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "أسبرين 300 ملغ + تيكاغريلور + هيبارين + تحويل لقثطرة مبكرة", points = 10, feedback = "صحيح. مضادا الصفيحات مع مضاد التخثر والقثطرة المبكرة هي أساس NSTEMI عالي الخطر.", nextNodeId = "n2"),
                    ErAction(label = "أسبرين فقط ومراقبة بلا مضاد تخثر ولا قثطرة", points = -5, feedback = "ناقص. الأسبرين وحده لا يكفي في NSTEMI عالي الخطر؛ يحتاج علاجًا مضادًا للصفيحات والتخثر وتقييمًا غازيًا.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة فورًا كما في STEMI", points = -10, feedback = "خطأ. الحالّات ليست علاجًا روتينيًا في NSTEMI وقد تضر؛ العلاج الغازي هو المسار الصحيح.", nextNodeId = "n2"),
                    ErAction(label = "تخريج المريض لأن التخطيط بلا ارتفاع ST", points = -10, feedback = "خطير. انخفاض ST مع تروبونين مرتفع هو احتشاء حقيقي ويحتاج قبولًا وعلاجًا فوريًا.", nextNodeId = null, endOutcome = "انتهت الحالة مبكرًا: مريض NSTEMI خُرّج بلا علاج وتدهور لاحقًا.")
                )
            ),
            ErNode(
                id = "n2",
                text = "القثطرة أظهرت تضيقًا شديدًا في الشريان الأمامي النازل وتمت معالجته. ما خطة ما بعد القثطرة؟",
                vitals = ErVitals(hr = 78, sbp = 132, dbp = 80, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "مضادا صفيحات مزدوج + ستاتين عالي الشدة + حاصرات بيتا ومثبط ACE حسب التحمل", points = 10, feedback = "صحيح. الوقاية الثانوية بعد ACS تجمع مضادات الصفيحات والستاتين وعلاج إعادة التأهيل القلبي.", nextNodeId = null, endOutcome = "أُنجزت الحالة: NSTEMI عولج بالقثطرة المبكرة والوقاية الثانوية."),
                    ErAction(label = "إيقاف مضاد P2Y12 بعد أسبوع لتجنب النزف", points = -10, feedback = "خطأ. الإيقاف المبكر لمضاد P2Y12 بعد الدعامة يرفع خطر خثار الدعامة.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: لا يُوقف مضاد P2Y12 مبكرًا بعد الدعامة."),
                    ErAction(label = "تخريج بلا ستاتين ولا متابعة", points = -5, feedback = "ناقص. الستاتين عالي الشدة والمتابعة جزء إلزامي من الوقاية الثانوية.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة: الوقاية الثانوية إلزامية.")
                )
            )
        ),
        endSummary = "الدرس: NSTEMI عالي الخطر ليس ألمًا حميدًا — مضادا صفيحات ومضاد تخثر وقثطرة مبكرة خلال 24 ساعة."
    ),
    ErCase(
        id = "a_svt",
        title = "تسرع فوق بطيني منتظم — SVT",
        presentation = "امرأة 29 سنة، خفقان مفاجئ منذ 30 دقيقة، ضيق نفس خفيف. التخطيط: تسرع منتظم ضيق المركب 180/د بلا موجات P واضحة. الضغط 118/72 وهي واعية تمامًا.",
        guidelineRef = "Brugada et al., 2019 ESC SVT Guidelines, Eur Heart J 2020;41:655-720, doi 10.1093/eurheartj/ehz467 و AHA ACLS 2020: مناورات مبهمة أولًا، ثم أدينوزين 6 ملغ وريدي سريع ثم 12 ملغ عند الحاجة في المريض المستقر.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "المريضة مستقرة هيموديناميكيًا. ما الخطوة الأولى؟",
                vitals = ErVitals(hr = 180, sbp = 118, dbp = 72, rr = 20, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مناورة فالسالفا المعدلة ثم أدينوزين 6 ملغ وريدي سريع مع رفع الذراع", points = 10, feedback = "صحيح. المناورة المبهمة ثم الأدينوزين السريع هما الخطوتان الأولى والثانية في SVT المستقر.", nextNodeId = "n2"),
                    ErAction(label = "صدمة كهربائية متزامنة فورًا رغم الاستقرار", points = -5, feedback = "غير ضروري. الصدمة تُحفظ لعدم الاستقرار أو فشل الأدوية؛ المريضة مستقرة وتستجيب غالبًا للأدينوزين.", nextNodeId = "n2"),
                    ErAction(label = "فيراباميل وريدي كخيار أول", points = -5, feedback = "غير مفضل أولًا. الفيراباميل قد يسبب هبوط ضغط وهو خيار ثانٍ بعد الأدينوزين في SVT ضيق المركب.", nextNodeId = "n2"),
                    ErAction(label = "مراقبة فقط بلا أي تدخل لأنها شابة", points = -5, feedback = "ناقص. SVT بسرعة 180 قد يتدهور؛ التدخل المبكر مطلوب رغم الاستقرار الحالي.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "عاد النظم جيبيًا بعد الأدينوزين (78/د). التخطيط بين النوبات طبيعي. ما الخطوة التالية؟",
                vitals = ErVitals(hr = 78, sbp = 122, dbp = 76, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة قصيرة + تحويل لقلبية لدراسة الاستئصال بالقثطرة عند التكرار", points = 10, feedback = "صحيح. الاستئصال بالقثطرة علاج شافٍ شائع لـ AVNRT المتكرر، والمتابعة القلبية إلزامية.", nextNodeId = null, endOutcome = "أُنجزت الحالة: SVT أُنهي بالأدينوزين مع خطة متابعة صحيحة."),
                    ErAction(label = "بدء مضاد تخثر دائم", points = -5, feedback = "خطأ. SVT بلا رجفان أذيني لا يحتاج مضاد تخثر دائمًا.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: لا مضاد تخثر روتيني في SVT."),
                    ErAction(label = "تخريج بلا أي توثيق ولا متابعة", points = -5, feedback = "ناقص. توثيق النظم والمتابعة يمنعان تكرارًا غير مشخص.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة: المتابعة القلبية مطلوبة.")
                )
            )
        ),
        endSummary = "الدرس: في SVT المستقر، مناورة مبهمة ثم أدينوزين سريع؛ الصدمة لعدم الاستقرار أو فشل الأدوية."
    ),
    ErCase(
        id = "a_af_rvr",
        title = "رجفان أذيني باستجابة بطينية سريعة",
        presentation = "رجل 71 سنة، خفقان وضيق نفس منذ يوم. التخطيط: رجفان أذيني غير منتظم 150/د، ضغط 128/80، ولا ألم صدري ولا وذمة رئوية. لا يعرف مدة الرجفان بدقة.",
        guidelineRef = "ESC 2024 AF Guidelines: ضبط السرعة أولًا في المستقر، وتقييم الخثرية بـ CHA2DS2-VA قبل أي تقويم نظم، وعدم تقويم بلا تغطية مضادة للتخثر عند مدة مجهولة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "المريض مستقر ولا علامات قصور حاد. ما التدبير الأول؟",
                vitals = ErVitals(hr = 150, sbp = 128, dbp = 80, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "ضبط السرعة بحاصرات بيتا أو ديلتيازيم + تقييم مضاد التخثر", points = 10, feedback = "صحيح. في AF المستقر، ضبط السرعة أولًا مع تقييم الحاجة لمضاد التخثر هو المسار الآمن.", nextNodeId = "n2"),
                    ErAction(label = "تقويم كهربائي فوري بلا مضاد تخثر رغم مدة مجهولة", points = -10, feedback = "خطير. تقويم AF بمدة مجهولة بلا تغطية مضادة للتخثر قد يحرر خثرة ويسبب سكتة.", nextNodeId = "n2"),
                    ErAction(label = "ديجوكسين كخيار وحيد رغم عدم وجود قصور قلب", points = -5, feedback = "غير مفضل. الديجوكسين بطيء ولا يضبط السرعة بالجهد؛ حاصرات بيتا أو ديلتيازيم أولى هنا.", nextNodeId = "n2"),
                    ErAction(label = "أدينوزين لإنهاء الرجفان", points = -10, feedback = "خطأ. الأدينوزين لا ينهي الرجفان الأذيني؛ قد يسبب توقفًا عابرًا بلا فائدة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "هبطت السرعة إلى 95/د والمريض مرتاح. درجة CHA2DS2-VA تساوي 3. ما القرار؟",
                vitals = ErVitals(hr = 95, sbp = 126, dbp = 78, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "بدء مضاد تخثر فموي + متابعة قلبية لخطة ضبط النظم أو السرعة", points = 10, feedback = "صحيح. الدرجة 3 تستدعي مضاد تخثر، والمتابعة تحدد استراتيجية النظم طويلة الأمد.", nextNodeId = null, endOutcome = "أُنجزت الحالة: AF ضُبطت سرعته وبُدئ مضاد التخثر."),
                    ErAction(label = "أسبرين وحده بدل مضاد التخثر", points = -5, feedback = "خطأ. الأسبرين لا يقي من سكتة AF كالمضاد الفموي عند الدرجات العالية.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: الأسبرين ليس بديلًا عن مضاد التخثر في AF."),
                    ErAction(label = "تخريج بلا مضاد تخثر", points = -10, feedback = "خطير. ترك AF بدرجة خطر عالية بلا وقاية يعرض المريض لسكتة قابلة للمنع.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ خطير: مضاد التخثر إلزامي هنا.")
                )
            )
        ),
        endSummary = "الدرس: في AF المستقر، اضبط السرعة أولًا، ولا تقوّم بلا تغطية مضادة للتخثر عند مدة مجهولة."
    ),
    ErCase(
        id = "a_flutter_rvr",
        title = "رفرفة أذينية باستجابة سريعة",
        presentation = "رجل 64 سنة، خفقان وتعب منذ ساعات. التخطيط: موجات رفرفة منشارية 300/د أذينيًا مع توصيل بطيني 150/د منتظم. الضغط 124/78 ولا قصور حاد.",
        guidelineRef = "ESC 2024 AF Guidelines (تشمل الرفرفة): ضبط السرعة ومضاد التخثر كالرجفان، والاستئصال بالقثطرة علاج شافٍ شائع للرفرفة النموذجية.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "المريض مستقر. ما التدبير؟",
                vitals = ErVitals(hr = 150, sbp = 124, dbp = 78, rr = 19, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "ضبط السرعة + مضاد تخثر حسب درجة الخطر + تحويل لدراسة الاستئصال", points = 10, feedback = "صحيح. الرفرفة تُعامل خثريًا كالرجفان، والاستئصال يعالج الدارة النموذجية بنجاح عالٍ.", nextNodeId = "n2"),
                    ErAction(label = "أدينوزين كعلاج نهائي للرفرفة", points = -5, feedback = "ناقص. الأدينوزين قد يكشف موجات الرفرفة بحجب عابر لكنه لا ينهيها.", nextNodeId = "n2"),
                    ErAction(label = "تقويم فوري بلا تقييم خثري", points = -10, feedback = "خطأ. الرفرفة تحمل خطر خثرة كالرجفان؛ التقويم يحتاج تغطية مضادة للتخثر عند الحاجة.", nextNodeId = "n2"),
                    ErAction(label = "تجاهلها لأنها منتظمة وليست رجفانًا", points = -10, feedback = "خطأ. الرفرفة السريعة تسبب قصورًا وخثرات كالرجفان ولا تُتجاهل.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "ضُبطت السرعة إلى 88/د. ما المتابعة؟",
                vitals = ErVitals(hr = 88, sbp = 120, dbp = 76, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "استمرار مضاد التخثر + موعد استئصال بالقثطرة", points = 10, feedback = "صحيح. الاستئصال علاج جذري للرفرفة النموذجية، ومضاد التخثر يستمر حسب الخطر.", nextNodeId = null, endOutcome = "أُنجزت الحالة: رفرفة ضُبطت وخطة استئصال وُضعت."),
                    ErAction(label = "إيقاف مضاد التخثر فور ضبط السرعة", points = -5, feedback = "خطأ. ضبط السرعة لا يلغي خطر الخثرة.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: مضاد التخثر لا يُوقف بضبط السرعة."),
                    ErAction(label = "تخريج بلا متابعة قلبية", points = -5, feedback = "ناقص. الرفرفة تحتاج متابعة وتقييمًا للاستئصال.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة.")
                )
            )
        ),
        endSummary = "الدرس: الرفرفة الأذينية ليست حميدة — ضبط سرعة ومضاد تخثر كالرجفان، والاستئصال علاج شافٍ غالبًا."
    ),
    ErCase(
        id = "a_vt_stable",
        title = "تسرع بطيني أحادي الشكل مستقر",
        presentation = "رجل 59 سنة، خفقان ودوخة خفيفة، قصة احتشاء قديم. التخطيط: تسرع واسع المركب منتظم 170/د، ضغط 112/70، واعٍ ولا ألم صدري ولا وذمة رئوية.",
        guidelineRef = "AHA ACLS 2020 و Zeppenfeld et al., 2022 ESC Ventricular Arrhythmias Guidelines, Eur Heart J 2022;43:3997-4126, doi 10.1093/eurheartj/ehac262: التسرع واسع المركب عند مريض بقلب بنيوي يُعامل كـ VT حتى يثبت العكس؛ أميودارون أو تقويم متزامن عند الاستقرار النسبي.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "التسرع واسع المركب والمريض مستقر نسبيًا. ما التدبير؟",
                vitals = ErVitals(hr = 170, sbp = 112, dbp = 70, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "أميودارون 150 ملغ وريدي خلال 10 دقائق مع مراقبة وتجهيز للتقويم", points = 10, feedback = "صحيح. الأميودارون خيار مقبول في VT المستقر، مع بقاء التقويم المتزامن جاهزًا عند التدهور.", nextNodeId = "n2"),
                    ErAction(label = "فيراباميل وريدي لاعتباره SVT", points = -10, feedback = "خطير. الفيراباميل في VT قد يسبب انهيارًا هيموديناميكيًا؛ واسع المركب مع قلب بنيوي هو VT حتى يثبت العكس.", nextNodeId = "n2"),
                    ErAction(label = "أدينوزين كعلاج أول للـ VT", points = -5, feedback = "خطأ. الأدينوزين لا يعالج VT أحادي الشكل ولا يُعتمد عليه هنا.", nextNodeId = "n2"),
                    ErAction(label = "مراقبة فقط وانتظار زواله تلقائيًا", points = -10, feedback = "خطير. VT مستمر قد يتدهور إلى رجفان بطيني؛ التدخل مطلوب.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "عاد النظم جيبيًا. الإيكو يظهر قذفًا منخفضًا وندبة قديمة. ما الخطوة التالية؟",
                vitals = ErVitals(hr = 82, sbp = 118, dbp = 74, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "قبول وعناية قلبية + تقييم ICD والعلاج الأمثل لقصور القلب", points = 10, feedback = "صحيح. VT مع قذف منخفض يستدعي تقييم ICD وعلاج قصور القلب الأمثل.", nextNodeId = null, endOutcome = "أُنجزت الحالة: VT عولج وتقييم ICD وُضع."),
                    ErAction(label = "تخريج فوري بلا تقييم ICD", points = -10, feedback = "خطير. VT مع قلب بنيوي خطر موت مفاجئ عالٍ ولا يُخرّج بلا تقييم.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ خطير: تقييم ICD إلزامي."),
                    ErAction(label = "مضاد تخثر وحده كعلاج", points = -5, feedback = "خطأ. مضاد التخثر لا يمنع تكرار VT.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة خاطئة.")
                )
            )
        ),
        endSummary = "الدرس: واسع المركب مع قلب بنيوي هو VT حتى يثبت العكس، والفيراباميل ممنوع فيه."
    ),
    ErCase(
        id = "a_vf_arrest",
        title = "توقف قلب برجفان بطيني — VF",
        presentation = "رجل 62 سنة انهار في الطوارئ وفقد الوعي. المراقب: رجفان بطيني، بلا نبض ولا تنفس طبيعي. لا إصابة ولا نزف ظاهر.",
        guidelineRef = "AHA ACLS 2020 و Focused Update 2023: صدمة مبكرة + ضغطات عالية الجودة + أدرينالين 1 ملغ كل 3-5 دقائق + أميودارون 300 ملغ بعد الصدمة الثالثة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "VF بلا نبض. ما التسلسل الأول؟",
                vitals = ErVitals(hr = null, sbp = null, dbp = null, rr = null, spo2 = null, gcs = 3),
                actions = listOf(
                    ErAction(label = "ضغطات صدر عالية الجودة فورًا + صدمة ثنائية الطور 120-200 جول بأسرع وقت", points = 10, feedback = "صحيح. الصدمة المبكرة مع ضغطات عالية الجودة هي ما ينقذ في VF؛ كل دقيقة تأخير تقلل البقاء.", nextNodeId = "n2"),
                    ErAction(label = "أدرينالين أولًا وانتظار قبل الصدمة", points = -5, feedback = "خطأ في الترتيب. في VF الصدمة أولًا ولا تُؤخر بالأدوية.", nextNodeId = "n2"),
                    ErAction(label = "تنبيب أولًا قبل الضغطات والصدمة", points = -10, feedback = "خطأ. تأخير الضغطات والصدمة لأجل التنبيب يقلل البقاء بشدة.", nextNodeId = "n2"),
                    ErAction(label = "صدمة متزامنة كما في SVT", points = -5, feedback = "خطأ تقني. VF يُصدم بلا تزامن لأن النظم فوضوي بلا مركب يُزامن عليه.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "بعد صدمتين، ما زال VF. ما الإضافة الدوائية؟",
                vitals = ErVitals(hr = null, sbp = null, dbp = null, rr = null, spo2 = null, gcs = 3),
                actions = listOf(
                    ErAction(label = "أدرينالين 1 ملغ كل 3-5 دقائق + أميودارون 300 ملغ وريدي بعد الصدمة الثالثة مع استمرار الضغطات", points = 10, feedback = "صحيح. هذا هو بروتوكول ACLS لـ VF المستمر مع عدم مقاطعة الضغطات إلا للصدمة.", nextNodeId = "n3"),
                    ErAction(label = "أميودارون فقط بلا أدرينالين", points = -5, feedback = "ناقص. الأدرينالين ركن في VF المستمر ولا يُستغنى عنه بالأميودارون وحده.", nextNodeId = "n3"),
                    ErAction(label = "إيقاف الضغطات مطولًا للتنبيب قبل الصدمة الثالثة", points = -10, feedback = "خطير. مقاطعة الضغطات المطولة تقلل البقاء؛ التنبيب لا يؤخر الصدمة والضغطات.", nextNodeId = "n3"),
                    ErAction(label = "أتروبين كدواء أول في VF", points = -5, feedback = "خطأ. الأتروبين ليس علاج VF؛ الأدرينالين والأميودارون هما الدواءان.", nextNodeId = "n3")
                )
            ),
            ErNode(
                id = "n3",
                text = "عاد النبض (ROSC) بعد الصدمة الثالثة. المريض ما زال فاقد الوعي. ما الأولوية؟",
                vitals = ErVitals(hr = 108, sbp = 98, dbp = 60, rr = 12, spo2 = 94, gcs = 6),
                actions = listOf(
                    ErAction(label = "تخطيط 12 مسرى + قثطرة عاجلة إن وُجد STEMI + عناية ورعاية ما بعد التوقف", points = 10, feedback = "صحيح. بعد ROSC، البحث عن سبب قابل للعلاج (انسداد إكليلي) والرعاية المركزة يحددان البقاء العصبي.", nextNodeId = null, endOutcome = "أُنجزت الحالة: VF أُدير ببروتوكول ACLS ثم رعاية ما بعد التوقف."),
                    ErAction(label = "تبريد شديد فوري إلى 30 درجة", points = -5, feedback = "خطأ. الهدف منع الحمى والحفاظ على حرارة طبيعية، لا تبريد شديد.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: التبريد الشديد غير موصى به."),
                    ErAction(label = "إيقاف المراقبة لأنه عاد النبض", points = -10, feedback = "خطير. ما بعد التوقف مرحلة حرجة باضطراب نظم ونكس محتمل.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: المراقبة بعد ROSC إلزامية.")
                )
            )
        ),
        endSummary = "الدرس: في VF، صدمة مبكرة وضغطات عالية الجودة ثم أدرينالين وأميودارون، وبعد ROSC ابحث عن السبب."
    ),
    ErCase(
        id = "a_pea_arrest",
        title = "توقف قلب بنشاط كهربائي بلا نبض — PEA",
        presentation = "امرأة 68 سنة انهارت بعد جراحة حديثة. المراقب: نظم كهربائي منظم بلا نبض محسوس ولا تنفس. البطن منتفخ قليلًا والضغط غير مقاس.",
        guidelineRef = "AHA ACLS 2020: في PEA لا صدمة — ضغطات + أدرينالين 1 ملغ كل 3-5 دقائق + البحث عن أسباب Hs و Ts القابلة للعلاج.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "PEA بلا نبض. ما التدبير الأول؟",
                vitals = ErVitals(hr = 70, sbp = null, dbp = null, rr = null, spo2 = null, gcs = 3),
                actions = listOf(
                    ErAction(label = "ضغطات عالية الجودة + أدرينالين 1 ملغ كل 3-5 دقائق وبحث عن سبب قابل للعلاج", points = 10, feedback = "صحيح. PEA لا يُصدم؛ العلاج ضغطات وأدرينالين وعلاج السبب.", nextNodeId = "n2"),
                    ErAction(label = "صدمة كهربائية فورًا كما في VF", points = -10, feedback = "خطأ. الصدمة لا تفيد في PEA وتؤخر الضغطات وعلاج السبب.", nextNodeId = "n2"),
                    ErAction(label = "أميودارون فورًا قبل الأدرينالين", points = -5, feedback = "خطأ. الأميودارون ليس علاج PEA؛ الأدرينالين هو الدواء الأول.", nextNodeId = "n2"),
                    ErAction(label = "انتظار عودة النبض تلقائيًا", points = -10, feedback = "خطير. PEA بلا إنعاش ينتهي بالاختناق والموت.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "الإيكو السريري يظهر انخماصًا وريديًا شديدًا وقلبًا شبه فارغ — نقص حجم حاد محتمل. ما الإضافة؟",
                vitals = ErVitals(hr = 88, sbp = null, dbp = null, rr = null, spo2 = null, gcs = 3),
                actions = listOf(
                    ErAction(label = "سوائل وريدية سريعة + دم عند النزف المشتبه + استمرار الإنعاش", points = 10, feedback = "صحيح. نقص الحجم من أسباب PEA القابلة للعلاج ويُعالج بالتعويض السريع مع استمرار الضغطات.", nextNodeId = null, endOutcome = "أُنجزت الحالة: PEA أُدير بعلاج سببه القابل للعلاج."),
                    ErAction(label = "مزيد من الصدمات", points = -10, feedback = "خطأ. تكرار الصدمة في PEA لا يعالج السبب.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: PEA لا يُصدم."),
                    ErAction(label = "إيقاف الإنعاش لعدم وجود صدمة", points = -10, feedback = "خطأ. عدم قابلية الصدمة ليست سببًا لإيقاف الإنعاش.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر غير مبرر.")
                )
            )
        ),
        endSummary = "الدرس: PEA لا يُصدم أبدًا — ضغطات وأدرينالين وعلاج السبب من قائمة Hs و Ts."
    ),
    ErCase(
        id = "a_asystole",
        title = "توقف قلب بانعدام نظم — Asystole",
        presentation = "رجل 74 سنة وُجد فاقد الوعي بلا نبض. المراقب: خط مسطح، ولا تنفس. مدة التوقف مجهولة والمراقب على قطب واحد فقط.",
        guidelineRef = "AHA ACLS 2020: تأكد من انعدام النظم بأكثر من قطب، ضغطات + أدرينالين، ولا صدمة في انعدام النظم المؤكد.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "خط مسطح على قطب واحد. ما أول ما تفعله؟",
                vitals = ErVitals(hr = null, sbp = null, dbp = null, rr = null, spo2 = null, gcs = 3),
                actions = listOf(
                    ErAction(label = "التحقق من الوصلات والتأكد بقطب آخر + بدء الضغطات والأدرينالين", points = 10, feedback = "صحيح. الخط المسطح قد يكون انفصال قطب أو VF ناعم؛ التأكد ثم الإنعاش دون تأخير.", nextNodeId = "n2"),
                    ErAction(label = "صدمة فورًا على الخط المسطح", points = -10, feedback = "خطأ. انعدام النظم المؤكد لا يُصدم وقد تتأذى عضلة القلب بالصدمة بلا فائدة.", nextNodeId = "n2"),
                    ErAction(label = "إعلان الوفاة فور رؤية الخط المسطح", points = -10, feedback = "خطأ. لا يُعلن قبل التأكد والإنعاش المناسب.", nextNodeId = "n2"),
                    ErAction(label = "أميودارون أولًا", points = -5, feedback = "خطأ. الأميودارون ليس علاج انعدام النظم.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تأكد انعدام النظم، ولا عودة نبض بعد دورتين وأدرينالين. ما المتابعة؟",
                vitals = ErVitals(hr = null, sbp = null, dbp = null, rr = null, spo2 = null, gcs = 3),
                actions = listOf(
                    ErAction(label = "استمرار ضغطات عالية الجودة + أدرينالين كل 3-5 دقائق + بحث عن أسباب قابلة للعلاج", points = 10, feedback = "صحيح. انعدام النظم المستمر يُدار بالإنعاش المتواصل والبحث عن سبب قابل للعلاج.", nextNodeId = null, endOutcome = "أُنجزت الحالة: انعدام النظم أُدير ببروتوكول ACLS الصحيح."),
                    ErAction(label = "صدمة أخيرة على أمل التحول", points = -5, feedback = "خطأ. الصدمة لا تحول انعدام النظم إلى نظم.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: انعدام النظم لا يُصدم."),
                    ErAction(label = "إعطاء كالسيوم روتيني لكل توقف", points = -5, feedback = "خطأ. الكالسيوم ليس روتينيًا في التوقف إلا لسبب محدد كفرط البوتاسيوم.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: لا كالسيوم روتيني في التوقف.")
                )
            )
        ),
        endSummary = "الدرس: تأكد من انعدام النظم أولًا، ثم ضغطات وأدرينالين بلا صدمة."
    ),
    ErCase(
        id = "a_chb",
        title = "حصار قلبي كامل عرضي — Complete Heart Block",
        presentation = "رجل 70 سنة، إغماء متكرر ودوخة. التخطيط: حصار AV كامل، أذيني 80 وبطيني 35 بمركب واسع. الضغط 88/58، متعرق وشاحب.",
        guidelineRef = "AHA ACLS 2020 Bradycardia: أتروبين 1 ملغ كل 3-5 دقائق (حد 3 ملغ) ثم تنظيم عبر الجلد أو تسريب دوبامين/أدرينالين ثم تنظيم عبر الوريد عند عدم الاستجابة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "حصار كامل عرضي مع هبوط ضغط. ما الخطوة الأولى؟",
                vitals = ErVitals(hr = 35, sbp = 88, dbp = 58, rr = 18, spo2 = 96, gcs = 14),
                actions = listOf(
                    ErAction(label = "أتروبين 1 ملغ وريدي مع تجهيز تنظيم عبر الجلد فورًا", points = 10, feedback = "صحيح. الأتروبين أولًا مع تجهيز التنظيم، لأن الحصار تحت العقدي قد لا يستجيب للأتروبين وحده.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا لتهدئة الأذين", points = -10, feedback = "خطير. حاصرات التوصيل تفاقم الحصار وقد تسبب توقفًا.", nextNodeId = "n2"),
                    ErAction(label = "مراقبة فقط لأن النبض 35 مقبول عند المسنين", points = -10, feedback = "خطأ. نبض 35 عرضي مع إغماء وهبوط ضغط حالة طارئة.", nextNodeId = "n2"),
                    ErAction(label = "أدينوزين لتوضيح التخطيط", points = -10, feedback = "خطير. الأدينوزين قد يوقف التوصيل المتبقي تمامًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "لم يستجب للأتروبين (نبض 36 وضغط 86/55). ما التالي؟",
                vitals = ErVitals(hr = 36, sbp = 86, dbp = 55, rr = 18, spo2 = 96, gcs = 14),
                actions = listOf(
                    ErAction(label = "تنظيم عبر الجلد + تسريب دوبامين أو أدرينالين ثم تنظيم عبر الوريد", points = 10, feedback = "صحيح. عند فشل الأتروبين، التنظيم هو العلاج الحاسم والجسر حتى الزرع الدائم.", nextNodeId = null, endOutcome = "أُنجزت الحالة: حصار كامل عولج بالتنظيم وتحويل لزرع دائم."),
                    ErAction(label = "مزيد من الأتروبين فوق 3 ملغ", points = -5, feedback = "خطأ. جرعة الأتروبين القصوى 3 ملغ؛ تجاوزها لا يفيد ويؤخر التنظيم.", nextNodeId = null, endOutcome = "انتهت الحالة بتأخير التنظيم: الحد الأقصى للأتروبين 3 ملغ."),
                    ErAction(label = "تخريج مع موعد روتيني", points = -10, feedback = "خطير. الحصار الكامل العرضي يحتاج قبولًا وتنظيمًا عاجلًا.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ خطير.")
                )
            )
        ),
        endSummary = "الدرس: الحصار الكامل العرضي: أتروبين ثم تنظيم فورًا عند عدم الاستجابة، ولا حاصرات توصيل أبدًا."
    ),
    ErCase(
        id = "a_sinus_brady",
        title = "بطء جيبي عرضي بعد احتشاء سفلي",
        presentation = "رجل 63 سنة، احتشاء سفلي قبل ساعات، الآن دوخة وغثيان. التخطيط: بطء جيبي 42/د، ضغط 92/60. لا حصار AV كامل ولا مركب واسع.",
        guidelineRef = "AHA ACLS 2020 Bradycardia و ESC 2023 ACS: البطء العرضي يُعالج بالأسباب ثم الأتروبين، والتنظيم عند عدم الاستجابة؛ وفي الاحتشاء السفلي قد يكون البطء عابرًا.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "بطء جيبي عرضي مع هبوط ضغط خفيف. ما التدبير؟",
                vitals = ErVitals(hr = 42, sbp = 92, dbp = 60, rr = 17, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "أتروبين 1 ملغ وريدي + مراجعة الأدوية المبطئة وعلاج الاحتشاء", points = 10, feedback = "صحيح. الأتروبين أولًا مع إزالة المسبب وعلاج الاحتشاء الأساسي.", nextNodeId = "n2"),
                    ErAction(label = "زرع منظم دائم فورًا بلا محاولة دوائية", points = -5, feedback = "غير ضروري أولًا. البطء الجيبي بعد احتشاء سفلي غالبًا عابر ويستجيب للأتروبين.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا فورًا للاحتشاء رغم البطء العرضي", points = -10, feedback = "خطير. حاصرات بيتا تفاقم البطء العرضي وتُؤجل حتى يستقر النظم.", nextNodeId = "n2"),
                    ErAction(label = "تجاهل البطء لأنه جيبي وليس حصارًا", points = -5, feedback = "خطأ. البطء العرضي مع هبوط ضغط يحتاج علاجًا سواء كان جيبيًا أم حصارًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تحسن النبض إلى 68 والضغط 118/72 بعد الأتروبين. ما المتابعة؟",
                vitals = ErVitals(hr = 68, sbp = 118, dbp = 72, rr = 16, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة قلبية مستمرة + استكمال علاج الاحتشاء ومراجعة الحاجة للتنظيم المؤقت", points = 10, feedback = "صحيح. المراقبة تحمي من نكس البطء، وعلاج الاحتشاء هو علاج السبب.", nextNodeId = null, endOutcome = "أُنجزت الحالة: بطء جيبي عولج مع علاج الاحتشاء."),
                    ErAction(label = "تخريج فوري", points = -5, feedback = "خطأ. مريض ما بعد احتشاء مع بطء حديث يحتاج مراقبة وقبولًا.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "إعطاء حاصرات بيتا بجرعة عالية فورًا", points = -5, feedback = "خطأ. تُعطى بحذر بعد استقرار النظم وليس بجرعة عالية فور البطء.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: البطء الجيبي العرضي يُعالج بالأسباب والأتروبين أولًا، وحاصرات التوصيل تُؤجل حتى الاستقرار."
    ),
    ErCase(
        id = "a_ape",
        title = "وذمة رئوية حادة",
        presentation = "امرأة 74 سنة، ضيق نفس شديد مفاجئ ليلًا، سعال برغوة وردية، لا تستطيع الاستلقاء. الضغط 190/110، خراخر منتشرة حتى الأكتاف، وSpO2 ‏86% على الهواء.",
        guidelineRef = "ESC 2021 Heart Failure و 2023 Focused Update: نترات وريدية + مدرات عروية + دعم تنفسي غير غازٍ (CPAP) في الوذمة الرئوية الحادة مع ارتفاع الضغط.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "وذمة رئوية حادة مع ارتفاع ضغط شديد. ما التدبير الفوري؟",
                vitals = ErVitals(hr = 118, sbp = 190, dbp = 110, rr = 30, spo2 = 86, gcs = 15),
                actions = listOf(
                    ErAction(label = "إجلاس + أكسجين + نترات وريدية + فوروسيميد وريدي + CPAP", points = 10, feedback = "صحيح. خفض ما قبل الحمل وما بعده مع إدرار البول ودعم الأكسجة يفك الوذمة بسرعة.", nextNodeId = "n2"),
                    ErAction(label = "سوائل وريدية سريعة لرفع الضغط", points = -10, feedback = "خطير. السوائل تفاقم الوذمة الرئوية؛ المريضة محتقنة لا ناقصة حجم.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا وريدية فورًا", points = -10, feedback = "خطأ. حاصرات بيتا الوريدية قد تفاقم القصور الحاد وتُؤجل حتى الاستقرار.", nextNodeId = "n2"),
                    ErAction(label = "مدر فموي فقط وانتظار", points = -5, feedback = "ناقص. المدر الفموي بطيء وغير كافٍ في وذمة حادة مهددة للحياة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تحسنت الأكسجة إلى 94% والضغط إلى 160/95 بعد العلاج. ما التالي؟",
                vitals = ErVitals(hr = 102, sbp = 160, dbp = 95, rr = 22, spo2 = 94, gcs = 15),
                actions = listOf(
                    ErAction(label = "قبول وعناية + تخطيط وإيكو وبحث عن محرض (إقفار، رجفان، ارتفاع ضغط، عدم التزام)", points = 10, feedback = "صحيح. الوذمة عرض؛ يجب علاج المحرض ومنع النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: وذمة رئوية عولجت مع بحث عن المحرض."),
                    ErAction(label = "تخريج فوري بعد التحسن الجزئي", points = -10, feedback = "خطير. الوذمة الحادة قد تنكس سريعًا بلا مراقبة وعلاج محرض.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر خطير."),
                    ErAction(label = "إيقاف كل العلاج الوريدي فورًا", points = -5, feedback = "خطأ. الإيقاف المفاجئ قبل الاستقرار الكامل يعيد الاحتقان.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر.")
                )
            )
        ),
        endSummary = "الدرس: في الوذمة الرئوية الحادة، نترات ومدر وريدي ودعم تنفسي، ولا سوائل ولا حاصرات بيتا وريدية."
    ),
    ErCase(
        id = "a_adhf",
        title = "قصور قلب مزمن متفاقم — ADHF",
        presentation = "رجل 68 سنة، قصور قلب معروف، ضيق نفس متفاقم خلال أسبوع، وذمة ساقين، زيادة وزن 4 كغ. الضغط 118/74، SpO2 ‏92%، وخراخر قاعدية.",
        guidelineRef = "ESC 2021 Heart Failure: مدرات عروية بالجرعة الكافية حتى زوال الاحتقان، مع علاج قصور القلب الأمثل ومراجعة المحرضات.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "احتقان واضح بلا صدمة ولا نقص أكسجة شديد. ما التدبير؟",
                vitals = ErVitals(hr = 96, sbp = 118, dbp = 74, rr = 22, spo2 = 92, gcs = 15),
                actions = listOf(
                    ErAction(label = "فوروسيميد وريدي بجرعة كافية + مراقبة البول والكهارل والوزن", points = 10, feedback = "صحيح. إزالة الاحتقان بالمدر الوريدي هي أساس ADHF بلا صدمة.", nextNodeId = "n2"),
                    ErAction(label = "مقويات تقلص (دوبوتامين) فورًا رغم ضغط طبيعي", points = -5, feedback = "غير ضروري. المقويات تُحفظ لنقص التروية والصدمة، لا للاحتقان وحده.", nextNodeId = "n2"),
                    ErAction(label = "سوائل وريدية", points = -10, feedback = "خطأ. المريض محتقن؛ السوائل تزيد الاحتقان.", nextNodeId = "n2"),
                    ErAction(label = "تخريج بمدر فموي بلا تقييم", points = -5, feedback = "ناقص. التفاقم الأسبوعي يحتاج تقييمًا ومدرًا وريديًا ومراجعة محرض.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "أدرّ 1.5 لتر وتحسن ضيق النفس، لكن الكرياتينين ارتفع قليلًا والبوتاسيوم 3.4. ما القرار؟",
                vitals = ErVitals(hr = 90, sbp = 114, dbp = 72, rr = 20, spo2 = 94, gcs = 15),
                actions = listOf(
                    ErAction(label = "استمرار إزالة الاحتقان بحذر + تعويض بوتاسيوم + مراقبة وظيفة الكلية", points = 10, feedback = "صحيح. ارتفاع كرياتينين طفيف مع إدرار ناجح لا يوقف إزالة الاحتقان؛ يُراقب ويُعوّض البوتاسيوم.", nextNodeId = null, endOutcome = "أُنجزت الحالة: ADHF أُزيل احتقانه مع مراقبة كلوية."),
                    ErAction(label = "إيقاف المدر نهائيًا لارتفاع الكرياتينين الطفيف", points = -5, feedback = "خطأ. إيقاف المدر يبقي الاحتقان وهو أخطر من ارتفاع طفيف عابر.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف غير مبرر للمدر."),
                    ErAction(label = "إعطاء مدر بجرعة مضاعفة بلا مراقبة كهارل", points = -5, feedback = "خطأ. الإدرار القوي بلا مراقبة يسبب نقص بوتاسيوم واضطراب نظم.", nextNodeId = null, endOutcome = "انتهت الحالة بإدرار غير مراقب.")
                )
            )
        ),
        endSummary = "الدرس: في ADHF بلا صدمة، أزل الاحتقان بالمدر الوريدي وراقب الكلية والكهارل، ولا مقويات بلا نقص تروية."
    ),
    ErCase(
        id = "a_cardiogenic_shock",
        title = "صدمة قلبية بعد احتشاء",
        presentation = "رجل 60 سنة، احتشاء أمامي واسع، الآن برودة أطراف، ضغط 78/50، بول شبه معدوم، لاكتات مرتفع، وخراخر رئوية. قذف القلب منخفض جدًا بالإيكو.",
        guidelineRef = "ESC 2023 ACS و ESC 2021 Heart Failure: في الصدمة القلبية، قثطرة طارئة + مقبض أوعية/مقوٍّ (نورأدرينالين/دوبوتامين) وتقييم دعم ميكانيكي، مع تجنب الإفراط بالسوائل.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "صدمة قلبية مع احتقان رئوي. ما التدبير الأول؟",
                vitals = ErVitals(hr = 118, sbp = 78, dbp = 50, rr = 26, spo2 = 90, gcs = 13),
                actions = listOf(
                    ErAction(label = "نورأدرينالين + دوبوتامين + قثطرة طارئة وتقييم دعم ميكانيكي", points = 10, feedback = "صحيح. إعادة التروية هي علاج السبب، والدعم الدوائي والميكانيكي جسر حتى التعافي.", nextNodeId = "n2"),
                    ErAction(label = "سوائل بكميات كبيرة وحدها", points = -10, feedback = "خطير. الإفراط بالسوائل في صدمة قلبية محتقنة يفاقم الوذمة الرئوية.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا وريدية لخفض النبض", points = -10, feedback = "خطير. حاصرات بيتا تثبط التقلص في الصدمة القلبية.", nextNodeId = "n2"),
                    ErAction(label = "مدر قوي وحده بلا دعم ضغط", points = -5, feedback = "ناقص وخطير. المدر وحده لا يرفع التروية في الصدمة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "ارتفع الضغط إلى 96/60 بعد الدعم. ما المتابعة؟",
                vitals = ErVitals(hr = 108, sbp = 96, dbp = 60, rr = 22, spo2 = 93, gcs = 14),
                actions = listOf(
                    ErAction(label = "عناية مركزة قلبية + مراقبة لاكتات وبول وضغط شرياني غازٍ ومراجعة دعم ميكانيكي", points = 10, feedback = "صحيح. الصدمة القلبية تحتاج مراقبة غازية وتقييمًا مستمرًا للدعم الميكانيكي.", nextNodeId = null, endOutcome = "أُنجزت الحالة: صدمة قلبية أُديرت بإعادة تروية ودعم."),
                    ErAction(label = "تخريج للجناح العادي", points = -10, feedback = "خطير. الصدمة القلبية حالة عناية مركزة لا جناح عادي.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ في مستوى الرعاية."),
                    ErAction(label = "إيقاف الدعم فور ارتفاع الضغط قليلًا", points = -5, feedback = "خطأ. الفطام يكون تدريجيًا بعد استقرار التروية.", nextNodeId = null, endOutcome = "انتهت الحالة بفطام مبكر.")
                )
            )
        ),
        endSummary = "الدرس: الصدمة القلبية علاجها إعادة تروية ودعم، لا سوائل غزيرة ولا حاصرات بيتا."
    ),
    ErCase(
        id = "a_tamponade",
        title = "اندحاس قلبي — Cardiac Tamponade",
        presentation = "رجل 55 سنة، ضيق نفس متفاقم، ألم صدري، ضغط 88/60 مع نبض متناقض، أوردة رقبية منتفخة، وأصوات قلب خافتة. الإيكو: انصباب كبير مع انخماص أذيني أيمن.",
        guidelineRef = "ESC 2015 Pericardial Diseases: الاندحاس تشخيص سريري-إيكو، والعلاج بزل التامور الموجه بالإيكو، مع تجنب موسعات الأوعية والمدرات قبل البزل.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ثلاثي بيك مع نبض متناقض وانصباب كبير. ما التدبير؟",
                vitals = ErVitals(hr = 118, sbp = 88, dbp = 60, rr = 26, spo2 = 91, gcs = 14),
                actions = listOf(
                    ErAction(label = "بزل تامور عاجل موجه بالإيكو مع دعم حجمي حذر وتجهيز جراحي", points = 10, feedback = "صحيح. البزل يحرر القلب المضغوط وهو العلاج الحاسم في الاندحاس.", nextNodeId = "n2"),
                    ErAction(label = "مدر قوي ونترات لخفض الضغط الوريدي", points = -10, feedback = "خطير. خفض ما قبل الحمل في الاندحاس ينهار به النتاج القلبي.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا لخفض النبض", points = -10, feedback = "خطأ. تسرع القلب تعويضي في الاندحاس وإبطاؤه يفاقم الصدمة.", nextNodeId = "n2"),
                    ErAction(label = "انتظار صورة الصدر قبل أي إجراء", points = -5, feedback = "خطأ. الإيكو والعلامات تكفي؛ التأخير في الاندحاس قاتل.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "بعد البزل تحسن الضغط إلى 112/70. السائل دموي. ما التالي؟",
                vitals = ErVitals(hr = 96, sbp = 112, dbp = 70, rr = 20, spo2 = 95, gcs = 15),
                actions = listOf(
                    ErAction(label = "إرسال السائل للتحليل + بحث عن سبب (ورم، إنتان، رض، تمزق) ومراقبة لصيقة للنكس", points = 10, feedback = "صحيح. البزل يعالج الضغط لكن السبب يجب أن يُشخص لمنع النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: اندحاس عولج بالبزل مع بحث عن السبب."),
                    ErAction(label = "تخريج فوري بعد تحسن الضغط", points = -10, feedback = "خطير. النكس شائع والسائل الدموي يحتاج تشخيصًا ومراقبة.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "إعطاء مضاد تخثر فورًا بلا تشخيص", points = -5, feedback = "خطأ. مضاد التخثر قد يزيد النزف التاموري قبل معرفة السبب.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: الاندحاس يُعالج بالبزل لا بالمدرات والموسعات؛ خفض ما قبل الحمل فيه ينهار النتاج."
    ),
    ErCase(
        id = "a_dissection_a",
        title = "تسلخ أبهر نوع A — Aortic Dissection",
        presentation = "رجل 58 سنة، ألم صدري ممزق ينتشر للظهر، ضغط 190/100 في الذراع اليمنى و150/90 في اليسرى، ونفخة أبهرية جديدة. التصوير المقطعي الوعائي: تسلخ يبدأ في الأبهر الصاعد.",
        guidelineRef = "ESC 2024 Peripheral Arterial and Aortic Diseases: تسلخ النوع A جراحة طارئة، مع خفض سرعة القلب أولًا ثم الضغط (هدف ضغط انقباضي 100-120) قبل أي موسع.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تسلخ نوع A مؤكد. ما ترتيب خفض الضغط؟",
                vitals = ErVitals(hr = 108, sbp = 190, dbp = 100, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "إسمولول أو لابيتالول وريدي أولًا ثم موسع عند الحاجة + جراحة قلب طارئة", points = 10, feedback = "صحيح. خفض سرعة القلب وقوة القذف أولًا يمنع امتداد التسلخ؛ الموسع وحده يزيد القذف ويوسع التسلخ.", nextNodeId = "n2"),
                    ErAction(label = "نيتروبروسيد وحده لخفض الضغط سريعًا", points = -10, feedback = "خطير. الموسع وحده يزيد سرعة القذف ويمدد التسلخ.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة لاشتباه احتشاء", points = -10, feedback = "قاتل. الحالّ في التسلخ يسبب نزفًا وتمزقًا مميتًا.", nextNodeId = "n2"),
                    ErAction(label = "مسكن فقط وانتظار الصباح للجراحة", points = -10, feedback = "خطير. تسلخ النوع A يمزق خلال ساعات؛ الجراحة طارئة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "استقر الضغط عند 115/70 وسرعة القلب 58. ما المتابعة حتى الجراحة؟",
                vitals = ErVitals(hr = 58, sbp = 115, dbp = 70, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة غازية + مسكن قوي + تجنب مضادات التخثر ونقل عاجل لجراحة القلب", points = 10, feedback = "صحيح. الألم يرفع الضغط والقذف، ومضادات التخثر ممنوعة قبل الجراحة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تسلخ نوع A ضُبط ونُقل لجراحة طارئة."),
                    ErAction(label = "إعطاء هيبارين تحضيرًا للقثطرة", points = -10, feedback = "خطير. الهيبارين في التسلخ الحاد يزيد النزف والتمزق.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ قاتل."),
                    ErAction(label = "تأجيل الجراحة لاستكمال فحوص روتينية", points = -5, feedback = "خطأ. التأخير في النوع A يرفع الوفيات بالساعة.", nextNodeId = null, endOutcome = "انتهت الحالة بتأخير غير مبرر.")
                )
            )
        ),
        endSummary = "الدرس: في تسلخ النوع A، حاصر بيتا أولًا ثم موسع، وجراحة طارئة، ولا حالّ ولا هيبارين."
    ),
    ErCase(
        id = "a_aaa_rupture",
        title = "تمزق أم دم أبهرية بطنية — Ruptured AAA",
        presentation = "رجل 76 سنة مدخن، ألم بطني مفاجئ ينتشر للظهر، كتلة بطنية نابضة، ضغط 82/55، شحوب وتعرق. الإيكو السريري: أبهر بطني 7 سم مع سائل حر.",
        guidelineRef = "ESC 2024 Peripheral Arterial and Aortic Diseases و ESVS 2024 AAA: التمزق المشتبه مع عدم استقرار لا ينتظر تصويرًا مطولًا — إنعاش متحفظ ونقل فوري لجراحة وعائية/EVAR.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تمزق أم دم مشتبه مع هبوط ضغط. ما التدبير؟",
                vitals = ErVitals(hr = 118, sbp = 82, dbp = 55, rr = 24, spo2 = 93, gcs = 13),
                actions = listOf(
                    ErAction(label = "إنعاش متحفظ (ضغط انقباضي 80-100) + دم + نقل فوري لجراحة وعائية دون تأخير بالتصوير", points = 10, feedback = "صحيح. رفع الضغط بقوة يزيد النزف، والتصوير المطول في غير المستقر يقتل؛ الجراحة هي الإنقاذ.", nextNodeId = "n2"),
                    ErAction(label = "سوائل غزيرة لرفع الضغط إلى طبيعي تمامًا", points = -10, feedback = "خطير. فرط الإنعاش يرفع ضغط التمزق ويزيد النزف.", nextNodeId = "n2"),
                    ErAction(label = "تصوير مقطعي مفصل قبل استدعاء الجراحة", points = -10, feedback = "خطير. في غير المستقر، استدعاء الجراحة يسبق التصوير المطول.", nextNodeId = "n2"),
                    ErAction(label = "مسكن ومراقبة فقط", points = -10, feedback = "خطأ. التمزق نزف داخلي قاتل يحتاج جراحة عاجلة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "وصل فريق الجراحة الوعائية والمريض في الطريق للعمليات. ما التجهيز؟",
                vitals = ErVitals(hr = 112, sbp = 88, dbp = 58, rr = 22, spo2 = 94, gcs = 13),
                actions = listOf(
                    ErAction(label = "زمر وتصالب دم + قثطرة وريدية كبيرة + إبلاغ بنك الدم والعناية", points = 10, feedback = "صحيح. التجهيز الدموي والوريدي قبل العمليات يختصر زمن النزف.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تمزق أم دم نُقل لجراحة عاجلة بتجهيز صحيح."),
                    ErAction(label = "إعطاء مضاد تخثر قبل الجراحة", points = -10, feedback = "خطأ. مضاد التخثر يفاقم نزف التمزق.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ قاتل."),
                    ErAction(label = "انتظار تحسن الضغط قبل النقل", points = -5, feedback = "خطأ. النقل للجراحة لا ينتظر ضغطًا مثاليًا.", nextNodeId = null, endOutcome = "انتهت الحالة بتأخير النقل.")
                )
            )
        ),
        endSummary = "الدرس: تمزق أم الدم غير المستقر: إنعاش متحفظ وجراحة فورية، لا تصوير مطول ولا فرط سوائل."
    ),
    ErCase(
        id = "a_htn_emergency",
        title = "طوارئ ارتفاع ضغط مع اعتلال دماغي",
        presentation = "رجل 52 سنة، صداع شديد وغباش رؤية وارتباك. الضغط 230/140، وذمة حليمة بالعين، ولا ألم صدري ولا ضيق نفس. الكرياتينين مرتفع حديثًا.",
        guidelineRef = "ESC 2024 Hypertension: في طوارئ الضغط، خفض تدريجي — لا أكثر من 25% من الانقباضي في الساعة الأولى — بموسع وريدي قابل للمعايرة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "طوارئ ضغط مع أذية أعضاء هدف. ما هدف الخفض؟",
                vitals = ErVitals(hr = 102, sbp = 230, dbp = 140, rr = 18, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(label = "لابيتالول أو نيكارديبين وريدي معايَر لخفض لا يتجاوز 25% في الساعة الأولى + عناية", points = 10, feedback = "صحيح. الخفض التدريجي يحمي الدماغ والكلية من نقص التروية المفاجئ.", nextNodeId = "n2"),
                    ErAction(label = "خفض الضغط إلى طبيعي خلال ساعة", points = -10, feedback = "خطير. الخفض المفاجئ يسبب سكتة ونقص تروية دماغية وكلية.", nextNodeId = "n2"),
                    ErAction(label = "نيفيديبين تحت اللسان فورًا", points = -10, feedback = "خطأ. الشكل تحت اللسان غير قابل للمعايرة ويسبب هبوطًا مفاجئًا.", nextNodeId = "n2"),
                    ErAction(label = "مسكن فقط ومراقبة", points = -5, feedback = "ناقص. اعتلال دماغي بارتفاع ضغط يحتاج خفضًا وريديًا مراقبًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "انخفض الضغط إلى 185/110 وتحسن الارتباك جزئيًا. ما التالي؟",
                vitals = ErVitals(hr = 94, sbp = 185, dbp = 110, rr = 17, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(label = "استمرار الخفض التدريجي نحو 160/100 خلال 2-6 ساعات + بحث عن سبب ثانوي", points = 10, feedback = "صحيح. الخفض التدريجي المتدرج هو المسار الآمن بعد الساعة الأولى.", nextNodeId = null, endOutcome = "أُنجزت الحالة: طوارئ ضغط أُديرت بخفض تدريجي مراقب."),
                    ErAction(label = "إيقاف الخافض فور تحسن الارتباك", points = -5, feedback = "خطأ. الإيقاف يعيد الارتفاع والأذية.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر."),
                    ErAction(label = "تخريج للمنزل", points = -10, feedback = "خطير. طوارئ الضغط تحتاج عناية ومراقبة.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج غير آمن.")
                )
            )
        ),
        endSummary = "الدرس: طوارئ الضغط تُخفض تدريجيًا بلا أكثر من 25% في الساعة الأولى، وبموسع وريدي معايَر."
    ),
    ErCase(
        id = "a_htn_urgency",
        title = "ارتفاع ضغط إسعافي بلا أذية أعضاء — Urgency",
        presentation = "امرأة 60 سنة، ضغط 195/115 في عيادة روتينية، صداع خفيف فقط، بلا غباش رؤية ولا ألم صدري ولا ضيق نفس ولا أذية كلوية أو عصبية. تخطيطها طبيعي.",
        guidelineRef = "ESC 2024 Hypertension: ارتفاع الضغط بلا أذية أعضاء هدف حادة لا يحتاج خفضًا وريديًا ولا قبولًا روتينيًا — علاج فموي ومتابعة خلال أيام.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ضغط مرتفع جدًا بلا أذية أعضاء. ما التدبير الصحيح؟",
                vitals = ErVitals(hr = 88, sbp = 195, dbp = 115, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "تقييم أذية أعضاء كامل + تعديل علاج فموي + متابعة خلال 2-3 أيام", points = 10, feedback = "صحيح. بلا أذية أعضاء، الخفض الفموي التدريجي والمتابعة القريبة هما العلاج؛ الخفض الوريدي يضر.", nextNodeId = "n2"),
                    ErAction(label = "خفض وريدي عاجل إلى طبيعي", points = -10, feedback = "خطأ. الخفض الوريدي السريع بلا أذية أعضاء يعرض لنقص تروية بلا فائدة.", nextNodeId = "n2"),
                    ErAction(label = "قبول عناية مركزة روتيني", points = -5, feedback = "غير ضروري. بلا أذية أعضاء، العناية لا تضيف فائدة.", nextNodeId = "n2"),
                    ErAction(label = "تجاهل الضغط لأنه بلا أعراض شديدة", points = -5, feedback = "خطأ. الضغط 195 يحتاج علاجًا ومتابعة قريبة حتى بلا أذية.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تقييم الأعضاء سليم والضغط 178/108 بعد علاج فموي. ما الخطة؟",
                vitals = ErVitals(hr = 84, sbp = 178, dbp = 108, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "تخريج مع خطة علاج فموي ومتابعة قريبة وتعليمات عودة عند أعراض خطر", points = 10, feedback = "صحيح. التخريج الآمن هنا مشروط بمتابعة قريبة وتعليمات واضحة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: ارتفاع إسعافي أُدير فمويًا بمتابعة قريبة."),
                    ErAction(label = "تخريج بلا متابعة", points = -5, feedback = "ناقص. المتابعة القريبة هي ما يجعل التخريج آمنًا.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج بلا متابعة."),
                    ErAction(label = "إبقاء المريضة أيامًا في الطوارئ للمراقبة", points = -5, feedback = "غير ضروري. المراقبة المطولة بلا أذية أعضاء لا تضيف فائدة.", nextNodeId = null, endOutcome = "انتهت الحالة بإشغال غير ضروري.")
                )
            )
        ),
        endSummary = "الدرس: فرّق الطوارئ عن الإسعافي: بلا أذية أعضاء، علاج فموي ومتابعة، لا خفض وريدي."
    ),
    ErCase(
        id = "a_pe",
        title = "انصمام رئوي عالي الخطر",
        presentation = "امرأة 48 سنة بعد سفر طويل، ضيق نفس مفاجئ وألم صدري جنبي، إغماء عابر. الضغط 82/55، نبض 122، SpO2 ‏88%، والإيكو: بطين أيمن متوسع ومجهد.",
        guidelineRef = "ESC 2024 Pulmonary Embolism: الانصمام عالي الخطر (هبوط ضغط) يُعالج بإعادة التروية — حالّ جهازي أو استئصال — مع هيبارين، ولا يُنتظر التصوير إن كان غير مستقر.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "انصمام مشتبه عالي الخطر مع صدمة. ما التدبير؟",
                vitals = ErVitals(hr = 122, sbp = 82, dbp = 55, rr = 28, spo2 = 88, gcs = 14),
                actions = listOf(
                    ErAction(label = "هيبارين + حالّ جهازي (ألتيبلاز 100 ملغ خلال ساعتين) عند غياب موانع النزف", points = 10, feedback = "صحيح. في الانصمام عالي الخطر، إعادة التروية تنقذ الحياة ولا يُنتظر استقرار المريض.", nextNodeId = "n2"),
                    ErAction(label = "هيبارين فقط وانتظار التحسن", points = -5, feedback = "ناقص في عالي الخطر. الهيبارين وحده لا يفك الانسداد المسبب للصدمة.", nextNodeId = "n2"),
                    ErAction(label = "تصوير مقطعي مطول قبل أي علاج رغم الصدمة", points = -10, feedback = "خطير. في غير المستقر، العلاج يسبق التصوير المطول عند قوة اشتباه عالية.", nextNodeId = "n2"),
                    ErAction(label = "مدر قوي لضيق النفس", points = -10, feedback = "خطأ. البطين الأيمن الفاشل يحتاج دعمًا لا إدرارًا ينهار به ما قبل الحمل.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تحسن الضغط إلى 108/68 بعد الحالّ. ما المتابعة؟",
                vitals = ErVitals(hr = 100, sbp = 108, dbp = 68, rr = 22, spo2 = 93, gcs = 15),
                actions = listOf(
                    ErAction(label = "استمرار مضاد التخثر + عناية + بحث عن خثار وريدي ومحرض", points = 10, feedback = "صحيح. بعد إعادة التروية، مضاد التخثر والبحث عن المصدر يمنعان النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: انصمام عالي الخطر أُعيدت ترويته."),
                    ErAction(label = "إيقاف مضاد التخثر بعد الحالّ", points = -10, feedback = "خطير. إيقاف مضاد التخثر يعيد الخثار.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف خطير."),
                    ErAction(label = "تخريج فوري", points = -10, feedback = "خطير. الانصمام عالي الخطر يحتاج عناية ومراقبة.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج غير آمن.")
                )
            )
        ),
        endSummary = "الدرس: الانصمام عالي الخطر يُعاد ترويته فورًا، والهيبارين وحده لا يكفي في الصدمة."
    ),
    ErCase(
        id = "a_dvt_phlegmasia",
        title = "خثار وريدي واسع — Phlegmasia Cerulea Dolens",
        presentation = "رجل 57 سنة، ساق يسرى متورمة جدًا زرقاء مؤلمة منذ يوم، مع ضيق نفس خفيف. الضغط 124/80، والساق باردة نسبيًا مع نبض ضعيف.",
        guidelineRef = "ESC 2024 Peripheral Arterial and Aortic Diseases و CHEST 2021 VTE: الخثار الواسع المهدد للطرف يحتاج مضاد تخثر فوري + تقييم وعائي عاجل لإزالة الخثرة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ساق زرقاء متورمة مهددة بالغرغرينا الوريدية. ما التدبير؟",
                vitals = ErVitals(hr = 102, sbp = 124, dbp = 80, rr = 20, spo2 = 95, gcs = 15),
                actions = listOf(
                    ErAction(label = "هيبارين فوري + رفع الساق + استدعاء وعائية عاجل لتقييم إزالة الخثرة", points = 10, feedback = "صحيح. مضاد التخثر يوقف الامتداد، وإزالة الخثرة تنقذ الطرف في الشكل الزرقي.", nextNodeId = "n2"),
                    ErAction(label = "جوارب ضاغطة قوية فورًا", points = -10, feedback = "خطير. الضغط على ساق زرقاء مهددة قد يفاقم نقص التروية.", nextNodeId = "n2"),
                    ErAction(label = "مسكن فقط وانتظار", points = -10, feedback = "خطأ. الشكل الزرقي يتطور لغرغرينا وفقد طرف خلال ساعات.", nextNodeId = "n2"),
                    ErAction(label = "مدر لتخفيف التورم", points = -5, feedback = "خطأ. التورم خثاري لا احتقاني؛ المدر لا يعالجه.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "بُدئ الهيبارين والفريق الوعائي يقيم إزالة الخثرة. ما المتابعة؟",
                vitals = ErVitals(hr = 96, sbp = 122, dbp = 78, rr = 19, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة علامات متلازمة الحيز + استمرار مضاد التخثر + قبول وعائي", points = 10, feedback = "صحيح. متلازمة الحيز مضاعفة مهددة للطرف ويجب التقاطها مبكرًا.", nextNodeId = null, endOutcome = "أُنجزت الحالة: خثار زرقي أُدير بمضاد تخثر وتقييم وعائي عاجل."),
                    ErAction(label = "تخريج بمضاد تخثر فموي فقط", points = -5, feedback = "ناقص. الطرف المهدد يحتاج قبولًا وتقييمًا تداخليًا لا تخريجًا.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج غير آمن لطرف مهدد."),
                    ErAction(label = "إيقاف الهيبارين عند تحسن الألم قليلًا", points = -10, feedback = "خطأ. تحسن الألم لا يعني زوال الخطر الخثاري.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف خطير.")
                )
            )
        ),
        endSummary = "الدرس: الساق الزرقاء المتورمة طارئ وعائي: هيبارين فوري وتقييم إزالة خثرة، لا ضغط ولا انتظار."
    ),
    ErCase(
        id = "a_limb_ischemia",
        title = "إقفار طرف حاد — Acute Limb Ischemia",
        presentation = "رجل 69 سنة، ساق يمنى شاحبة باردة مؤلمة فجأة منذ ساعتين، بلا نبض محسوس، مع خدر متزايد وضعف حركة خفيف. قصة رجفان أذيني بلا مضاد تخثر منتظم.",
        guidelineRef = "ESC 2024 Peripheral Arterial and Aortic Diseases: الإقفار الحاد طارئ وعائي — هيبارين فوري وإعادة تروية عاجلة، وكل ساعة تأخير تهدد الطرف.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ساق بلا نبض مع خدر وضعف حركة. ما التدبير؟",
                vitals = ErVitals(hr = 108, sbp = 138, dbp = 82, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "هيبارين وريدي فوري + استدعاء وعائية عاجل لإعادة التروية", points = 10, feedback = "صحيح. الهيبارين يوقف امتداد الخثرة وإعادة التروية العاجلة تنقذ الطرف.", nextNodeId = "n2"),
                    ErAction(label = "تصوير مطول قبل استدعاء الجراحة", points = -5, feedback = "خطأ في الترتيب. التصوير لا يؤخر استدعاء الوعائية في إقفار مهدد.", nextNodeId = "n2"),
                    ErAction(label = "تدفئة الساق ومسكن وانتظار", points = -10, feedback = "خطير. الإقفار يتطور لموت عضلي لا رجعة فيه خلال ساعات.", nextNodeId = "n2"),
                    ErAction(label = "مضاد تخثر فموي فقط وتخريج", points = -10, feedback = "خطأ. الإقفار المهدد يحتاج هيبارين وريديًا وتداخلًا عاجلًا لا علاجًا فمويًا بالمنزل.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تمت إعادة التروية وتحسن النبض والدفء. ما المضاعفة التي تُراقب؟",
                vitals = ErVitals(hr = 96, sbp = 132, dbp = 80, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة متلازمة إعادة التروية والحيز + استمرار مضاد التخثر + قبول وعائي", points = 10, feedback = "صحيح. بعد إعادة التروية، وذمة الحيز وفرط البوتاسيوم وأذية الكلية مضاعفات يجب التقاطها.", nextNodeId = null, endOutcome = "أُنجزت الحالة: إقفار طرف أُعيدت ترويته مع مراقبة ما بعدها."),
                    ErAction(label = "تخريج فوري بعد عودة النبض", points = -5, feedback = "خطأ. ما بعد إعادة التروية يحتاج مراقبة لصيقة.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "إيقاف مضاد التخثر فور عودة النبض", points = -5, feedback = "خطأ. مضاد التخثر يستمر لمنع إعادة الخثار.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف غير مبرر.")
                )
            )
        ),
        endSummary = "الدرس: الإقفار الحاد المهدد: هيبارين فوري وإعادة تروية عاجلة، والتأخير يساوي فقد الطرف."
    ),
    ErCase(
        id = "a_mesenteric",
        title = "إقفار مساريقي حاد — Acute Mesenteric Ischemia",
        presentation = "امرأة 72 سنة، ألم بطني شديد مفاجئ غير متناسب مع الفحص (بطن لين نسبيًا)، مع رجفان أذيني ولاكتات مرتفع. الضغط 132/80 ولا علامات التهاب صفاق واضحة بعد.",
        guidelineRef = "ESVS 2025 Clinical Practice Guidelines on the Management of Diseases of the Mesenteric and Renal Arteries and Veins (Koelemay et al., Eur J Vasc Endovasc Surg 2025, doi 10.1016/j.ejvs.2025.06.010) و WSES: الألم غير المتناسب مع الفحص يستدعي تصويرًا مقطعيًا وعائيًا عاجلًا وهيبارين وجراحة عاجلة عند الشك العالي.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ألم بطني شديد بفحص متواضع ولاكتات مرتفع. ما الخطوة؟",
                vitals = ErVitals(hr = 112, sbp = 132, dbp = 80, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "تصوير مقطعي وعائي للبطن عاجل + هيبارين + استدعاء جراحة", points = 10, feedback = "صحيح. التصوير الوعائي هو الاختبار الحاسم، والهيبارين والجراحة لا ينتظران تدهور الفحص.", nextNodeId = "n2"),
                    ErAction(label = "مسكن ومراقبة حتى يظهر التهاب الصفاق", points = -10, feedback = "خطير. انتظار التهاب الصفاق يعني موت أمعاء؛ التشخيص يجب أن يسبقه.", nextNodeId = "n2"),
                    ErAction(label = "صورة بطن بسيطة فقط", points = -5, feedback = "ناقص. الصورة البسيطة لا تنفي الإقفار المساريقي.", nextNodeId = "n2"),
                    ErAction(label = "مضاد حموضة لاشتباه التهاب معدة", points = -10, feedback = "خطأ. ألم شديد غير متناسب مع لاكتات ورجفان ليس التهاب معدة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "التصوير: انسداد شريان مساريقي علوي مع أمعاء ما زالت قابلة للإنقاذ. ما التدبير؟",
                vitals = ErVitals(hr = 108, sbp = 128, dbp = 78, rr = 19, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "إعادة تروية عاجلة (جراحية أو تداخلية) + استمرار هيبارين + عناية", points = 10, feedback = "صحيح. إعادة التروية المبكرة قبل النخر هي ما ينقذ الأمعاء والحياة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: إقفار مساريقي شُخص مبكرًا وأُعيدت ترويته."),
                    ErAction(label = "صادات فقط وانتظار", points = -10, feedback = "خطأ. الصادات لا تعيد التروية للأمعاء المقفرة.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ: إعادة التروية هي العلاج."),
                    ErAction(label = "تخريج بعد تحسن الألم بالمسكن", points = -10, feedback = "خطير. تحسن الألم بالمسكن يخفي موت الأمعاء.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج قاتل.")
                )
            )
        ),
        endSummary = "الدرس: ألم بطني شديد غير متناسب مع الفحص هو إقفار مساريقي حتى يثبت العكس — تصوير وعائي وهيبارين وجراحة."
    ),
    ErCase(
        id = "a_ischemic_stroke",
        title = "سكتة إقفارية حادة ضمن نافذة الحالّ",
        presentation = "رجل 67 سنة، ضعف مفاجئ بالجانب الأيمن وعسر كلام منذ 90 دقيقة. الضغط 168/95، سكر 140، ولا نزف ولا جراحة حديثة ولا مضاد تخثر.",
        guidelineRef = "AHA/ASA 2019 Acute Ischemic Stroke Guidelines: ألتيبلاز 0.9 ملغ/كغ خلال 4.5 ساعات (10% جرعة أولية) بعد نفي النزف بالتصوير، وضغط أقل من 185/110 قبل الحالّ.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "سكتة حادة منذ 90 دقيقة. ما التسلسل؟",
                vitals = ErVitals(hr = 88, sbp = 168, dbp = 95, rr = 17, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(label = "تصوير مقطعي للدماغ فورًا لنفي النزف + تقييم أهلية الحالّ", points = 10, feedback = "صحيح. نفي النزف بالتصوير هو بوابة الحالّ، والوقت دماغ.", nextNodeId = "n2"),
                    ErAction(label = "حالّ فورًا بلا تصوير", points = -10, feedback = "قاتل. الحالّ في نزف دماغي يفاقم النزف؛ التصوير أولًا دائمًا.", nextNodeId = "n2"),
                    ErAction(label = "أسبرين فقط وانتظار حتى انتهاء النافذة", points = -5, feedback = "خطأ. الأسبرين لا يغني عن الحالّ ضمن النافذة عند الأهلية.", nextNodeId = "n2"),
                    ErAction(label = "خفض ضغط شديد إلى طبيعي قبل التصوير", points = -5, feedback = "خطأ. الخفض الشديد قبل الحالّ يقلل تروية الدماغ؛ الهدف أقل من 185/110 فقط.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "التصوير بلا نزف ولا موانع، والضغط 172/98. ما القرار؟",
                vitals = ErVitals(hr = 86, sbp = 172, dbp = 98, rr = 17, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(label = "ألتيبلاز 0.9 ملغ/كغ (10% جرعة أولية) + مراقبة عصبية وضغط لصيقة", points = 10, feedback = "صحيح. ضمن النافذة وبلا موانع، الحالّ يحسن النتائج العصبية.", nextNodeId = null, endOutcome = "أُنجزت الحالة: سكتة إقفارية عولجت بالحالّ ضمن النافذة."),
                    ErAction(label = "تأجيل الحالّ لمراقبة التحسن التلقائي", points = -5, feedback = "خطأ. التأجيل يهدر النافذة؛ الفائدة تقل مع كل دقيقة.", nextNodeId = null, endOutcome = "انتهت الحالة بتأخير أهدر النافذة."),
                    ErAction(label = "هيبارين علاجي فورًا بدل الحالّ", points = -5, feedback = "خطأ. الهيبارين الحاد لا يحل محل الحالّ في السكتة الحادة.", nextNodeId = null, endOutcome = "انتهت الحالة بخيار خاطئ.")
                )
            )
        ),
        endSummary = "الدرس: في السكتة الإقفارية، صوّر لنفي النزف ثم حالّ ضمن 4.5 ساعات عند الأهلية — الوقت دماغ."
    ),
    ErCase(
        id = "a_ich",
        title = "نزف دماغي داخل المتن مع ارتفاع ضغط شديد",
        presentation = "رجل 61 سنة، صداع مفاجئ شديد وقيء وضعف يساري. الضغط 210/120، التصوير: نزف داخل المتن 18 مل بلا توسع بطيني. يأخذ وارفارين وINR يساوي 3.2.",
        guidelineRef = "AHA/ASA 2022 Intracerebral Hemorrhage Guidelines: خفض ضغط تدريجي نحو 140 انقباضي، وعكس مضاد التخثر فورًا (مركز معقد البروثرومبين + فيتامين K للوارفارين).",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "نزف دماغي على وارفارين مع ضغط شديد. ما الأولوية؟",
                vitals = ErVitals(hr = 92, sbp = 210, dbp = 120, rr = 18, spo2 = 96, gcs = 12),
                actions = listOf(
                    ErAction(label = "عكس وارفارين فورًا (PCC + فيتامين K) + خفض ضغط تدريجي نحو 140 + عناية عصبية", points = 10, feedback = "صحيح. عكس مضاد التخثر يوقف توسع النزف، والخفض التدريجي يحمي الدماغ.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة لاشتباه سكتة إقفارية", points = -10, feedback = "قاتل. الحالّ في نزف دماغي يفاقمه.", nextNodeId = "n2"),
                    ErAction(label = "أسبرين فورًا", points = -10, feedback = "خطأ. مضاد الصفيحات في نزف حاد يزيد النزف.", nextNodeId = "n2"),
                    ErAction(label = "خفض ضغط مفاجئ إلى 110 انقباضي", points = -10, feedback = "خطير. الخفض المفاجئ الشديد يقلل تروية الدماغ حول النزف.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "عُكس INR وانخفض الضغط إلى 155/95. ما المتابعة؟",
                vitals = ErVitals(hr = 88, sbp = 155, dbp = 95, rr = 17, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(label = "مراقبة عصبية لصيقة + تصوير متكرر عند التدهور + استشارة جراحة عصبية", points = 10, feedback = "صحيح. توسع النزف يحدث مبكرًا والمراقبة والجراحة عند الحاجة تنقذان.", nextNodeId = null, endOutcome = "أُنجزت الحالة: نزف دماغي عُولج بعكس التخثر وخفض تدريجي."),
                    ErAction(label = "تخريج للجناح العادي", points = -10, feedback = "خطير. النزف الحاد حالة عناية ومراقبة لصيقة.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ في مستوى الرعاية."),
                    ErAction(label = "إعادة وارفارين فورًا", points = -10, feedback = "خطأ. إعادة مضاد التخثر في نزف حاد قرار متأخر ومدروس لا فوري.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ قاتل.")
                )
            )
        ),
        endSummary = "الدرس: في النزف الدماغي، اعكس مضاد التخثر فورًا واخفض الضغط تدريجيًا، ولا حالّ ولا أسبرين."
    ),
    ErCase(
        id = "a_myocarditis",
        title = "التهاب عضلة قلب خاطف — Fulminant Myocarditis",
        presentation = "شاب 24 سنة، بعد إنفلونزا بأسبوع: ضيق نفس شديد، ألم صدري، إغماء عابر. الضغط 86/58، تروبونين مرتفع جدًا، والإيكو: قذف 25% مع وذمة جدارية.",
        guidelineRef = "ESC 2023 Cardiomyopathies و ESC 2021 Heart Failure: التهاب العضلة الخاطف يحتاج عناية ودعمًا هيموديناميكيًا ودعمًا ميكانيكيًا عند الصدمة، مع رنين/خزعة لتشخيص السبب.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "صدمة قلبية محتملة بالتهاب عضلة. ما التدبير؟",
                vitals = ErVitals(hr = 122, sbp = 86, dbp = 58, rr = 28, spo2 = 90, gcs = 14),
                actions = listOf(
                    ErAction(label = "عناية مركزة قلبية + دعم بمقويات/مقبضات + تقييم دعم ميكانيكي ورنين/خزعة", points = 10, feedback = "صحيح. الشكل الخاطف يتدهور سريعًا والدعم الميكانيكي المبكر جسر للتعافي.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة كاحتشاء دون إيكو", points = -5, feedback = "خطأ. التهاب العضلة يحاكي الاحتشاء؛ الإيكو والرنين يفرقان قبل الحالّ.", nextNodeId = "n2"),
                    ErAction(label = "مضادات التهاب غير ستيرويدية بجرعات عالية كعلاج أول", points = -5, feedback = "غير مفضل. مضادات الالتهاب عالية الجرعة قد تفاقم التهاب العضلة الحاد.", nextNodeId = "n2"),
                    ErAction(label = "تخريج بمضاد حيوي فموي", points = -10, feedback = "خطير. صدمة قلبية محتملة لا تُخرّج بمضاد حيوي.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "استقر الضغط بدعم دوائي والقذف ما زال منخفضًا. ما المتابعة؟",
                vitals = ErVitals(hr = 104, sbp = 100, dbp = 64, rr = 22, spo2 = 93, gcs = 15),
                actions = listOf(
                    ErAction(label = "علاج قصور قلب أمثل تدريجي + تقييم اضطراب نظم وICD لاحقًا + منع جهد شاق", points = 10, feedback = "صحيح. بعد المرحلة الحادة، علاج القصور وتقييم الموت المفاجئ يحددان المصير.", nextNodeId = null, endOutcome = "أُنجزت الحالة: التهاب عضلة خاطف أُدير بدعم وتقييم سببي."),
                    ErAction(label = "عودة فورية للرياضة التنافسية", points = -10, feedback = "خطير. الجهد الشاق بعد التهاب عضلة يرفع خطر اضطراب نظم قاتل.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ خطير."),
                    ErAction(label = "إيقاف كل علاج عند تحسن الضغط", points = -5, feedback = "خطأ. علاج القصور يستمر ويُعدل تدريجيًا.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر.")
                )
            )
        ),
        endSummary = "الدرس: التهاب العضلة الخاطف صدمة قلبية حتى يثبت العكس — دعم مبكر وتشخيص سببي، لا جهد ولا مضادات التهاب عالية."
    ),
    ErCase(
        id = "a_pericarditis",
        title = "التهاب تامور حاد — Acute Pericarditis",
        presentation = "رجل 34 سنة، ألم صدري حاد يزداد بالاستلقاء والشهيق ويتحسن بالجلوس للأمام، مع حرارة خفيفة. التخطيط: ارتفاع ST منتشر مع انخفاض PR، ولا انصباب كبير بالإيكو.",
        guidelineRef = "ESC 2015 Pericardial Diseases: أسبرين أو مضاد التهاب غير ستيرويدي + كولشيسين خط أول، والستيرويد ليس خطًا أولًا.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "التهاب تامور حاد بلا اندحاس. ما العلاج الأول؟",
                vitals = ErVitals(hr = 98, sbp = 124, dbp = 78, rr = 19, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "أسبرين أو مضاد التهاب غير ستيرويدي + كولشيسين 0.5 ملغ", points = 10, feedback = "صحيح. الكولشيسين مع مضاد الالتهاب يقلل النكس وهو خط العلاج الأول.", nextNodeId = "n2"),
                    ErAction(label = "ستيرويد بجرعة عالية فورًا", points = -5, feedback = "غير مفضل أولًا. الستيرويد يزيد النكس ويُحفظ لأسباب محددة أو فشل الخط الأول.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة لارتفاع ST", points = -10, feedback = "خطأ. ارتفاع ST المنتشر مع انخفاض PR في التهاب التامور ليس STEMI؛ الحالّ يضر.", nextNodeId = "n2"),
                    ErAction(label = "بزل تامور رغم غياب الاندحاس", points = -5, feedback = "غير ضروري. البزل يُحفظ للاندحاس أو الاشتباه الإنتاني/الورمي.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تحسن الألم خلال 48 ساعة. ما المتابعة؟",
                vitals = ErVitals(hr = 84, sbp = 122, dbp = 76, rr = 17, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "إكمال مضاد الالتهاب + كولشيسين 3 أشهر + تقييد جهد حتى زوال الأعراض والواسمات", points = 10, feedback = "صحيح. الكولشيسين المطول وتقييد الجهد يمنعان النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: التهاب تامور عولج بالخط الأول."),
                    ErAction(label = "إيقاف الكولشيسين فور تحسن الألم", points = -5, feedback = "خطأ. الإيقاف المبكر يرفع النكس.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر."),
                    ErAction(label = "عودة فورية لرياضة تنافسية", points = -5, feedback = "خطأ. الجهد يُقيّد حتى الشفاء الكامل.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ في تقييد الجهد.")
                )
            )
        ),
        endSummary = "الدرس: التهاب التامور الحاد: مضاد التهاب + كولشيسين، والستيرويد ليس خطًا أولًا، وارتفاع ST المنتشر ليس STEMI."
    ),
    ErCase(
        id = "a_endocarditis_emboli",
        title = "التهاب شغاف إنتاني مع صمات جهازية",
        presentation = "رجل 58 سنة، حرارة مستمرة أسبوعين، نفخة جديدة، وآفات جلدية صمية وبقعة شبكية. الإيكو: خضرة على الصمام الأبهري 12 ملم مع قلس متوسط. مزارع الدم لم تُؤخذ بعد.",
        guidelineRef = "ESC 2023 Infective Endocarditis: مزارع دم قبل الصادات، إيكو عاجل، وصادات مبكرة، وجراحة مبكرة عند قصور قلب أو إنتان غير مضبوط أو خطر صمي عالٍ.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "اشتباه التهاب شغاف قوي. ما التسلسل؟",
                vitals = ErVitals(hr = 108, sbp = 122, dbp = 76, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "3 مجموعات مزارع دم قبل الصادات + إيكو عاجل + صادات تجريبية مبكرة", points = 10, feedback = "صحيح. المزارع قبل الصادات تحدد العامل، والتأخير بالصادات يفاقم الإنتان والصمات.", nextNodeId = "n2"),
                    ErAction(label = "صادات قبل المزارع", points = -5, feedback = "خطأ في الترتيب. الصادات قبل المزارع تعقّم المزارع وتضيع التشخيص.", nextNodeId = "n2"),
                    ErAction(label = "مضاد تخثر لمنع الصمات", points = -10, feedback = "خطير. مضاد التخثر لا يمنع صمات الشغاف وقد يحول صمة دماغية لنزف.", nextNodeId = "n2"),
                    ErAction(label = "مسكن وخافض حرارة فقط", points = -10, feedback = "خطأ. التهاب الشغاف إنتان قلبي مهدد للحياة لا يُعالج بخافض حرارة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "المزارع: مكورات عنقودية ذهبية، والخضرة 12 ملم مع صمات سابقة. ما القرار؟",
                vitals = ErVitals(hr = 104, sbp = 118, dbp = 74, rr = 19, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "استشارة جراحة قلب مبكرة لخطر صمي عالٍ + صادات وريدية مطولة", points = 10, feedback = "صحيح. الخضرة الكبيرة مع صمات سابقة تستدعي جراحة مبكرة لمنع صمة قاتلة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: التهاب شغاف أُدير بمزارع وصادات وجراحة مبكرة."),
                    ErAction(label = "صادات فموية قصيرة وتخريج", points = -10, feedback = "خطير. التهاب الشغاف يحتاج صادات وريدية مطولة ومراقبة.", nextNodeId = null, endOutcome = "انتهت الحالة بعلاج ناقص خطير."),
                    ErAction(label = "تأجيل الجراحة حتى صمة دماغية جديدة", points = -10, feedback = "خطأ. الجراحة المبكرة تمنع الصمة التالية لا تنتظرها.", nextNodeId = null, endOutcome = "انتهت الحالة بتأجيل قاتل.")
                )
            )
        ),
        endSummary = "الدرس: في التهاب الشغاف، مزارع قبل صادات، وصادات مبكرة، وجراحة مبكرة عند خطر صمي عالٍ."
    ),
    ErCase(
        id = "a_valve_thrombosis",
        title = "خثار صمام ميكانيكي — Prosthetic Valve Thrombosis",
        presentation = "امرأة 55 سنة بصمام ميترالي ميكانيكي، ضيق نفس متفاقم، نفخة جديدة، وINR تحت العلاجي 1.3. الإيكو: تدرج عالٍ عبر الصمام مع كتلة متحركة.",
        guidelineRef = "ESC 2021 Valvular Heart Disease و AHA/ACC 2020 Valve Guidelines: خثار الصمام الميكانيكي طارئ — هيبارين وتقييم جراحة مقابل حالّ حسب حجم الخثرة والأعراض.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "خثار صمام ميكانيكي مشتبه مع قصور حاد. ما التدبير؟",
                vitals = ErVitals(hr = 112, sbp = 118, dbp = 74, rr = 26, spo2 = 91, gcs = 15),
                actions = listOf(
                    ErAction(label = "هيبارين وريدي + إيكو عاجل وتقييم فريق صمامات لجراحة أو حالّ", points = 10, feedback = "صحيح. الهيبارين يوقف الامتداد، وفريق الصمامات يقرر الجراحة مقابل الحالّ حسب الخطر.", nextNodeId = "n2"),
                    ErAction(label = "مدر فقط لضيق النفس", points = -5, feedback = "ناقص. المدر لا يحل انسداد الصمام الميكانيكي.", nextNodeId = "n2"),
                    ErAction(label = "حالّ فورًا بلا تقييم جراحي ولا إيكو مفصل", points = -5, feedback = "خطأ في الترتيب. الحالّ له موانع ومخاطر صمية؛ القرار لفريق الصمامات.", nextNodeId = "n2"),
                    ErAction(label = "زيادة وارفارين فموي فقط ومراقبة", points = -10, feedback = "خطأ. الوارفارين الفموي بطيء ولا يكفي في انسداد صمام حاد.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "الخثرة كبيرة والمريضة غير مستقرة. الفريق قرر جراحة طارئة. ما التجهيز؟",
                vitals = ErVitals(hr = 110, sbp = 110, dbp = 70, rr = 24, spo2 = 92, gcs = 15),
                actions = listOf(
                    ErAction(label = "استمرار هيبارين حتى الجراحة + دعم تنفسي وعناية + إيقاف وارفارين", points = 10, feedback = "صحيح. التجهيز الجراحي مع هيبارين قابل للإيقاف هو المسار الآمن.", nextNodeId = null, endOutcome = "أُنجزت الحالة: خثار صمام أُدير بفريق صمامات وجراحة طارئة."),
                    ErAction(label = "تأجيل الجراحة لرفع INR بالوارفارين", points = -10, feedback = "خطأ. التأجيل في انسداد صمام غير مستقر قاتل.", nextNodeId = null, endOutcome = "انتهت الحالة بتأجيل قاتل."),
                    ErAction(label = "تخريج بعد تحسن طفيف", points = -10, feedback = "خطير. انسداد صمام ميكانيكي لا يُخرّج.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج قاتل.")
                )
            )
        ),
        endSummary = "الدرس: خثار الصمام الميكانيكي طارئ صمامات: هيبارين وفريق صمامات، والوارفارين الفموي وحده لا يكفي."
    ),
    ErCase(
        id = "a_pacemaker_failure",
        title = "فشل التقاط منظم ضربات — Failure to Capture",
        presentation = "رجل 78 سنة بمنظم دائم، دوخة وإغماء عابر. المراقب: نبضات تنظيم بلا التقاط بطيني تالٍ، نبض بطيني ذاتي 38/د. الضغط 96/62.",
        guidelineRef = "Glikson et al., 2021 ESC Cardiac Pacing Guidelines, Eur Heart J 2021, doi 10.1093/eurheartj/ehab364 و AHA ACLS Bradycardia: فشل التقاط يُقيّم بالأسباب (بطارية، سلك، كهارل، أدوية) مع تنظيم عبر الجلد كجسر عند الأعراض.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "فشل التقاط عرضي. ما التدبير؟",
                vitals = ErVitals(hr = 38, sbp = 96, dbp = 62, rr = 17, spo2 = 96, gcs = 14),
                actions = listOf(
                    ErAction(label = "تنظيم عبر الجلد كجسر + فحص جهاز وبطارية وسلك وكهارل وأدوية", points = 10, feedback = "صحيح. الجسر يحمي من التوقف، وفحص الجهاز يحدد السبب القابل للإصلاح.", nextNodeId = "n2"),
                    ErAction(label = "زيادة مخرجات المنظم من سرير الطوارئ بلا فحص جهاز متخصص", points = -5, feedback = "ناقص. تعديل الإعدادات يحتاج فحص جهاز متخصص؛ الجسر أولًا عند الأعراض.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا لتنظيم النبض", points = -10, feedback = "خطير. حاصرات التوصيل تفاقم البطء في فشل التقاط.", nextNodeId = "n2"),
                    ErAction(label = "تخريج وموعد روتيني للجهاز", points = -10, feedback = "خطير. فشل التقاط عرضي يحتاج قبولًا وإصلاحًا عاجلًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "الكهارل: بوتاسيوم 6.1، والبطارية منخفضة. ما التالي؟",
                vitals = ErVitals(hr = 40, sbp = 98, dbp = 64, rr = 17, spo2 = 96, gcs = 14),
                actions = listOf(
                    ErAction(label = "علاج فرط البوتاسيوم + تحويل لتبديل بطارية/إصلاح سلك عاجل", points = 10, feedback = "صحيح. فرط البوتاسيوم يرفع عتبة التقاط، والبطارية المنخفضة تحتاج تبديلًا.", nextNodeId = null, endOutcome = "أُنجزت الحالة: فشل التقاط أُدير بجسر وعلاج سببه."),
                    ErAction(label = "علاج فرط البوتاسيوم فقط وتجاهل البطارية", points = -5, feedback = "ناقص. البطارية المنخفضة ستعيد الفشل.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة."),
                    ErAction(label = "إيقاف التنظيم عبر الجلد فورًا", points = -10, feedback = "خطير. إيقاف الجسر قبل إصلاح السبب يعيد البطء العرضي.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف جسر مبكر.")
                )
            )
        ),
        endSummary = "الدرس: فشل التقاط عرضي: جسر عبر الجلد ثم ابحث عن البطارية والسلك والكهارل."
    ),
    ErCase(
        id = "a_pmt",
        title = "تسرع متوسّط بالمنظم — Pacemaker-Mediated Tachycardia",
        presentation = "رجل 72 سنة بمنظم ثنائي الحجرة، خفقان مفاجئ 140/د منتظم. التخطيط: تسرع بتنظيم بطيني مع موجة P مرتدة قبل كل نبضة منظمة. الضغط 122/76 ومستقر.",
        guidelineRef = "Glikson et al., 2021 ESC Cardiac Pacing Guidelines, Eur Heart J 2021, doi 10.1093/eurheartj/ehab364: التسرع المتوسط بالمنظم دارة إعادة دخول عبر المنظم — يُكسر بالمغناطيس أو إعادة البرمجة (إطالة PVARP).",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تسرع متوسط بالمنظم مستقر. ما التدبير؟",
                vitals = ErVitals(hr = 140, sbp = 122, dbp = 76, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مغناطيس فوق المنظم أو إعادة برمجة (إطالة PVARP) عبر فحص الجهاز", points = 10, feedback = "صحيح. كسر الدارة عبر المنظم نفسه هو العلاج؛ الأدوية المضادة للنظم لا تكسر دارة المنظم.", nextNodeId = "n2"),
                    ErAction(label = "أدينوزين لإنهاء التسرع", points = -5, feedback = "غير مجدٍ. الأدينوزين قد يحجب العقدة عابرًا لكن الدارة عبر المنظم تعود.", nextNodeId = "n2"),
                    ErAction(label = "صدمة كهربائية فورًا رغم الاستقرار", points = -5, feedback = "غير ضروري. المريض مستقر والتسرع يُكسر ببرمجة المنظم.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا بجرعة عالية", points = -5, feedback = "ناقص. حاصرات بيتا لا تكسر دارة المنظم.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "انكسر التسرع وعاد النظم المنظم الطبيعي 70/د. ما المتابعة؟",
                vitals = ErVitals(hr = 70, sbp = 124, dbp = 78, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "إعادة برمجة دائمة لمنع النكس + متابعة جهاز دورية", points = 10, feedback = "صحيح. إطالة PVARP وخوارزميات منع التسرع تمنع النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تسرع متوسط بالمنظم كُسر وأُعيدت برمجته."),
                    ErAction(label = "تخريج بلا إعادة برمجة", points = -5, feedback = "ناقص. بلا إعادة برمجة سيتكرر التسرع.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة."),
                    ErAction(label = "استبدال المنظم فورًا", points = -5, feedback = "غير ضروري. إعادة البرمجة تحل المشكلة غالبًا.", nextNodeId = null, endOutcome = "انتهت الحالة بإجراء غير ضروري.")
                )
            )
        ),
        endSummary = "الدرس: التسرع المتوسط بالمنظم يُكسر بالمغناطيس أو إعادة البرمجة، لا بالأدينوزين ولا بالصدمة الروتينية."
    ),
    ErCase(
        id = "a_icd_storm",
        title = "عاصفة كهربائية — Electrical Storm / ICD Shocks",
        presentation = "رجل 66 سنة بـ ICD، تلقى 5 صدمات خلال ساعة مع خفقان متكرر. التخطيط بين الصدمات: VT أحادي الشكل متكرر. الضغط 108/70 وهو واعٍ وقلق جدًا.",
        guidelineRef = "Zeppenfeld et al., 2022 ESC Ventricular Arrhythmias Guidelines, Eur Heart J 2022;43:3997-4126, doi 10.1093/eurheartj/ehac262 و AHA ACLS: العاصفة الكهربائية تُعالج بحاصرات بيتا وأميودارون وتهدئة وتصحيح كهارل، مع تقييم استئصال عند التكرار.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "عاصفة VT مع صدمات متكررة. ما التدبير؟",
                vitals = ErVitals(hr = 148, sbp = 108, dbp = 70, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "حاصرات بيتا + أميودارون وريدي + تهدئة + تصحيح بوتاسيوم ومغنيسيوم + فحص جهاز", points = 10, feedback = "صحيح. كسر الحلقة الودية وتصحيح الكهارل مع مضاد نظم هو أساس علاج العاصفة.", nextNodeId = "n2"),
                    ErAction(label = "تعطيل ICD بالمغناطيس فورًا بلا علاج مضاد نظم", points = -10, feedback = "خطير. تعطيل ICD بلا علاج يترك VT قاتلًا بلا حماية.", nextNodeId = "n2"),
                    ErAction(label = "صدمات خارجية متكررة بدل علاج السبب", points = -5, feedback = "ناقص. الصدمة تنهي النوبة لا العاصفة؛ يجب كسر الحلقة الدوائية.", nextNodeId = "n2"),
                    ErAction(label = "تخريج بعد توقف الصدمات ساعة واحدة", points = -10, feedback = "خطير. العاصفة تنكس وتحتاج عناية وتقييم استئصال.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "هدأت العاصفة بعد العلاج. ما المتابعة؟",
                vitals = ErVitals(hr = 88, sbp = 116, dbp = 74, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "عناية + تحويل لاستئصال بالقثطرة عند التكرار + مراجعة إعدادات ICD", points = 10, feedback = "صحيح. الاستئصال يقلل نكس العاصفة عند المرضى المناسبين.", nextNodeId = null, endOutcome = "أُنجزت الحالة: عاصفة كهربائية هُدئت مع خطة استئصال."),
                    ErAction(label = "إيقاف حاصرات بيتا عند الهدوء", points = -10, feedback = "خطأ. إيقاف حاصرات بيتا يعيد العاصفة.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف خطير."),
                    ErAction(label = "تخريج بلا فحص جهاز", points = -5, feedback = "ناقص. فحص الجهاز يكشف صدمات غير مناسبة أو خللًا.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة.")
                )
            )
        ),
        endSummary = "الدرس: العاصفة الكهربائية تُهدأ بحاصرات بيتا وأميودارون وتهدئة وتصحيح كهارل، لا بتعطيل ICD وحده."
    ),
    ErCase(
        id = "a_digoxin",
        title = "تسمم بالديجوكسين — Digoxin Toxicity",
        presentation = "امرأة 79 سنة على ديجوكسين، غثيان وقيء واضطراب رؤية صفراء وخفقان. التخطيط: رجفان أذيني مع بطء وخوارج بطينية متكررة. بوتاسيوم 5.8 وكرياتينين مرتفع.",
        guidelineRef = "AHA ACLS Toxicology و FDA Digoxin Immune Fab: أوقف الديجوكسين، صحح البوتاسيوم، وأعطِ أضداد Fab عند اضطراب نظم مهدد للحياة أو فرط بوتاسيوم شديد.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تسمم ديجوكسين مشتبه مع اضطراب نظم وفرط بوتاسيوم. ما التدبير؟",
                vitals = ErVitals(hr = 52, sbp = 112, dbp = 70, rr = 18, spo2 = 96, gcs = 14),
                actions = listOf(
                    ErAction(label = "إيقاف ديجوكسين + مراقبة قلبية + أضداد Fab عند اضطراب مهدد للحياة", points = 10, feedback = "صحيح. إيقاف الدواء وFab هما العلاج النوعي عند التسمم الشديد.", nextNodeId = "n2"),
                    ErAction(label = "تقويم كهربائي فورًا لاضطراب النظم", points = -10, feedback = "خطير. التقويم في تسمم ديجوكسين قد يسبب رجفانًا مقاومًا.", nextNodeId = "n2"),
                    ErAction(label = "كالسيوم وريدي سريع لفرط البوتاسيوم", points = -5, feedback = "غير مفضل. الكالسيوم السريع في تسمم ديجوكسين قد يفاقم السمية؛ يُستخدم بحذر شديد عند الحاجة القصوى.", nextNodeId = "n2"),
                    ErAction(label = "زيادة جرعة ديجوكسين لضبط الرجفان", points = -10, feedback = "قاتل. زيادة الدواء السام تفاقم التسمم.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "أُعطي Fab وتحسن النظم جزئيًا. ما المتابعة؟",
                vitals = ErVitals(hr = 68, sbp = 118, dbp = 74, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة لصيقة + تصحيح بوتاسيوم ومغنيسيوم + مراجعة جرعة الديجوكسين ووظيفة الكلية", points = 10, feedback = "صحيح. التسمم ينكس مع تحرر الديجوكسين، والمراقبة ومراجعة الجرعة تمنعان التكرار.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تسمم ديجوكسين عولج بـ Fab ومراقبة."),
                    ErAction(label = "استئناف ديجوكسين بنفس الجرعة فورًا", points = -10, feedback = "خطأ. الاستئناف بنفس الجرعة مع قصور كلوي يعيد التسمم.", nextNodeId = null, endOutcome = "انتهت الحالة باستئناف خطير."),
                    ErAction(label = "تخريج فوري بعد Fab", points = -5, feedback = "خطأ. المراقبة بعد Fab إلزامية لنكس محتمل.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر.")
                )
            )
        ),
        endSummary = "الدرس: تسمم الديجوكسين: أوقف الدواء، تجنب التقويم والكالسيوم السريع، وFab عند التهديد للحياة."
    ),
    ErCase(
        id = "a_bb_overdose",
        title = "جرعة زائدة بحاصرات بيتا — Beta-Blocker Overdose",
        presentation = "رجل 45 سنة تناول جرعة كبيرة من ميتوبرولول انتحاريًا. الآن نبض 38، ضغط 78/48، دوخة شديدة، وسكر 65. لا تسرع ولا وذمة رئوية.",
        guidelineRef = "AHA 2023 Focused Update on Poisoning: في جرعة حاصرات بيتا الزائدة، غلوكاغون + أنسولين عالي الجرعة مع سكر + مقبضات، والتنظيم غالبًا غير مجدٍ وحده.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "بطء وهبوط ضغط بعد جرعة حاصرات بيتا زائدة. ما التدبير؟",
                vitals = ErVitals(hr = 38, sbp = 78, dbp = 48, rr = 16, spo2 = 96, gcs = 13),
                actions = listOf(
                    ErAction(label = "غلوكاغون وريدي + أنسولين عالي الجرعة مع سكر + دعم ضغط وسوائل", points = 10, feedback = "صحيح. الغلوكاغون والأنسولين عالي الجرعة يتجاوزان الحصار، والتنظيم وحده غالبًا لا يرفع الضغط.", nextNodeId = "n2"),
                    ErAction(label = "أتروبين وحده كعلاج كافٍ", points = -5, feedback = "ناقص. الأتروبين غالبًا غير مجدٍ في جرعة زائدة شديدة؛ يحتاج علاجًا نوعيًا.", nextNodeId = "n2"),
                    ErAction(label = "تنظيم دائم فوري", points = -5, feedback = "غير مجدٍ وحده. التنظيم قد يرفع النبض لكن النتاج يبقى منخفضًا بلا علاج نوعي.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا إضافية لخفض النبض أكثر", points = -10, feedback = "قاتل. زيادة الحاصر تفاقم الصدمة.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "ارتفع الضغط إلى 98/60 بعد الغلوكاغون والأنسولين. ما المتابعة؟",
                vitals = ErVitals(hr = 52, sbp = 98, dbp = 60, rr = 17, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(label = "عناية + مراقبة سكر وبوتاسيوم لصيقة + تقييم نفسي بعد الاستقرار", points = 10, feedback = "صحيح. الأنسولين عالي الجرعة يسبب نقص سكر وبوتاسيوم، والجرعة الانتحارية تحتاج تقييمًا نفسيًا.", nextNodeId = null, endOutcome = "أُنجزت الحالة: جرعة حاصرات بيتا زائدة عولجت علاجًا نوعيًا."),
                    ErAction(label = "تخريج فوري بعد تحسن الضغط", points = -10, feedback = "خطير. أثر الجرعة الزائدة مطول ويحتاج مراقبة.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "إيقاف الأنسولين فورًا بلا مراقبة سكر", points = -5, feedback = "خطأ. الفطام والمراقبة تدريجيان.", nextNodeId = null, endOutcome = "انتهت الحالة بفطام غير مراقب.")
                )
            )
        ),
        endSummary = "الدرس: جرعة حاصرات بيتا الزائدة: غلوكاغون وأنسولين عالي الجرعة، والأتروبين وحده غالبًا لا يكفي."
    ),
    ErCase(
        id = "a_ccb_overdose",
        title = "جرعة زائدة بحاصرات قنوات الكالسيوم — CCB Overdose",
        presentation = "امرأة 52 سنة تناولت جرعة كبيرة من أملوديبين وديلتيازيم. ضغط 72/45، نبض 52، سكر 210، وبرودة أطراف. لا وذمة رئوية بعد.",
        guidelineRef = "AHA 2023 Focused Update on Poisoning و ACMT: في جرعة CCB الزائدة، كالسيوم وريدي + أنسولين عالي الجرعة + مقبضات، مع عناية مبكرة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "صدمة وعائية بعد جرعة CCB زائدة. ما التدبير؟",
                vitals = ErVitals(hr = 52, sbp = 72, dbp = 45, rr = 18, spo2 = 95, gcs = 13),
                actions = listOf(
                    ErAction(label = "كالسيوم وريدي + أنسولين عالي الجرعة مع سكر + نورأدرينالين", points = 10, feedback = "صحيح. الكالسيوم والأنسولين عالي الجرعة والمقبضات هي ثلاثية علاج جرعة CCB الزائدة.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا لخفض النبض", points = -10, feedback = "خطير. حاصرات بيتا تفاقم الصدمة في جرعة CCB.", nextNodeId = "n2"),
                    ErAction(label = "سوائل غزيرة وحدها", points = -5, feedback = "ناقص. السوائل وحدها لا تعكس توسع الأوعية الشديد.", nextNodeId = "n2"),
                    ErAction(label = "أتروبين وحده", points = -5, feedback = "ناقص. الأتروبين لا يعالج توسع الأوعية في جرعة CCB.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "ارتفع الضغط إلى 96/60. ما المتابعة؟",
                vitals = ErVitals(hr = 62, sbp = 96, dbp = 60, rr = 17, spo2 = 96, gcs = 14),
                actions = listOf(
                    ErAction(label = "عناية + مراقبة سكر وكالسيوم وبوتاسيوم + استمرار الدعم حتى زوال أثر الجرعة", points = 10, feedback = "صحيح. أثر CCB مطول خاصة بالأشكال المديدة، والمراقبة تمنع النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: جرعة CCB زائدة عولجت بالثلاثية."),
                    ErAction(label = "تخريج بعد تحسن أولي", points = -10, feedback = "خطير. الأشكال المديدة تنكس بعد ساعات.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "إيقاف كل الدعم فورًا", points = -5, feedback = "خطأ. الفطام تدريجي بعد استقرار مطول.", nextNodeId = null, endOutcome = "انتهت الحالة بفطام مبكر.")
                )
            )
        ),
        endSummary = "الدرس: جرعة CCB الزائدة: كالسيوم وأنسولين عالي الجرعة ومقبضات، لا حاصرات بيتا ولا سوائل وحدها."
    ),
    ErCase(
        id = "a_torsades",
        title = "تورساد دي بوانت — Torsades de Pointes",
        presentation = "امرأة 68 سنة على مضاد حيوي مطيل QT، إغماء متكرر. التخطيط: تسرع متعدد الأشكال واسع المركب مع QT طويل جدًا، بين النوبات QTc يساوي 540 ميلي ثانية. بوتاسيوم 3.1 ومغنيسيوم منخفض.",
        guidelineRef = "Zeppenfeld et al., 2022 ESC Ventricular Arrhythmias Guidelines, Eur Heart J 2022;43:3997-4126, doi 10.1093/eurheartj/ehac262 و AHA ACLS: تورساد يُعالج بإيقاف مطيل QT + كبريتات مغنيسيوم 2 غ وريدي + تصحيح بوتاسيوم فوق 4.5، وتجنب الأميودارون.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تورساد مع QT طويل ونقص بوتاسيوم ومغنيسيوم. ما التدبير؟",
                vitals = ErVitals(hr = 150, sbp = 104, dbp = 68, rr = 20, spo2 = 95, gcs = 14),
                actions = listOf(
                    ErAction(label = "إيقاف مطيل QT + كبريتات مغنيسيوم 2 غ وريدي + تصحيح بوتاسيوم", points = 10, feedback = "صحيح. المغنيسيوم يثبت الغشاء رغم مستواه، وإيقاف المسبب وتصحيح البوتاسيوم يمنعان النكس.", nextNodeId = "n2"),
                    ErAction(label = "أميودارون كعلاج أول", points = -10, feedback = "خطير. الأميودارون يطيل QT وقد يفاقم تورساد.", nextNodeId = "n2"),
                    ErAction(label = "فيراباميل وريدي", points = -10, feedback = "خطأ. الفيراباميل لا يعالج تورساد وقد يسبب انهيارًا.", nextNodeId = "n2"),
                    ErAction(label = "مراقبة فقط لأن النوبات قصيرة", points = -10, feedback = "خطير. تورساد يتدهور لرجفان بطيني.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "توقفت النوبات لكن QT ما زال طويلًا. ما المتابعة؟",
                vitals = ErVitals(hr = 78, sbp = 118, dbp = 74, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة + تنظيم فوق سرعي أو إيزوبروتيرينول عند النكس + مراجعة كل الأدوية المطيلة QT", points = 10, feedback = "صحيح. تسريع النظم يقصر QT ويمنع النكس المتكرر.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تورساد عولج بالمغنيسيوم وإيقاف المسبب."),
                    ErAction(label = "استئناف المضاد الحيوي المطيل QT", points = -10, feedback = "خطأ. استئناف المسبب يعيد تورساد.", nextNodeId = null, endOutcome = "انتهت الحالة باستئناف المسبب."),
                    ErAction(label = "تخريج بلا مراقبة QT", points = -5, feedback = "خطأ. QT الطويل يحتاج مراقبة حتى يعود طبيعيًا.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر.")
                )
            )
        ),
        endSummary = "الدرس: تورساد: أوقف مطيل QT، أعطِ مغنيسيوم وصحح البوتاسيوم، ولا أميودارون."
    ),
    ErCase(
        id = "a_wpw_af",
        title = "رجفان أذيني مع WPW — Pre-excited AF",
        presentation = "رجل 31 سنة، خفقان شديد ودوخة. التخطيط: تسرع غير منتظم واسع المركب 190/د مع موجات دلتا بين النوبات سابقًا. الضغط 102/68 ومستقر نسبيًا.",
        guidelineRef = "Brugada et al., 2019 ESC SVT Guidelines, Eur Heart J 2020;41:655-720, doi 10.1093/eurheartj/ehz467 و ESC 2024 AF: في AF مع WPW، حاصرات العقدة AV ممنوعة (أدينوزين، حاصرات بيتا، ديلتيازيم، ديجوكسين)؛ العلاج بروكاييناميد أو تقويم متزامن.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "AF غير منتظم واسع المركب مع WPW معروف. ما الممنوع وما العلاج؟",
                vitals = ErVitals(hr = 190, sbp = 102, dbp = 68, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "بروكاييناميد وريدي أو تقويم متزامن عند عدم الاستقرار، وتجنب حاصرات العقدة تمامًا", points = 10, feedback = "صحيح. حصر العقدة يحوّل التوصيل كله للطريق الإضافي ويسبب رجفانًا بطينيًا.", nextNodeId = "n2"),
                    ErAction(label = "أدينوزين لإنهاء التسرع", points = -10, feedback = "قاتل محتمل. الأدينوزين يحصر العقدة ويسرّع التوصيل عبر الطريق الإضافي.", nextNodeId = "n2"),
                    ErAction(label = "ديلتيازيم لضبط السرعة", points = -10, feedback = "قاتل محتمل. الديلتيازيم حاصر عقدة ممنوع في AF مع WPW.", nextNodeId = "n2"),
                    ErAction(label = "ديجوكسين لضبط السرعة", points = -10, feedback = "خطير. الديجوكسين يقصر فترة الطريق الإضافي ويزيد الخطر.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "عاد النظم جيبيًا بعد بروكاييناميد. ما المتابعة؟",
                vitals = ErVitals(hr = 76, sbp = 118, dbp = 74, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "تحويل لاستئصال الطريق الإضافي بالقثطرة + تثقيف بتجنب حاصرات العقدة", points = 10, feedback = "صحيح. الاستئصال علاج شافٍ لـ WPW ويمنع النكس القاتل.", nextNodeId = null, endOutcome = "أُنجزت الحالة: AF مع WPW عولج دون حاصرات عقدة ومع خطة استئصال."),
                    ErAction(label = "وصف ديلتيازيم للمنزل", points = -10, feedback = "قاتل محتمل. حاصرات العقدة ممنوعة في WPW مع AF.", nextNodeId = null, endOutcome = "انتهت الحالة بوصفة خطيرة."),
                    ErAction(label = "تخريج بلا تحويل للاستئصال", points = -5, feedback = "ناقص. WPW العرضي يحتاج تقييم استئصال.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة.")
                )
            )
        ),
        endSummary = "الدرس: AF مع WPW: لا أدينوزين ولا حاصرات بيتا ولا ديلتيازيم ولا ديجوكسين — بروكاييناميد أو تقويم ثم استئصال."
    ),
    ErCase(
        id = "a_hyperkalemia",
        title = "فرط بوتاسيوم مع تغيرات تخطيطية",
        presentation = "رجل 71 سنة بقصور كلوي مزمن، ضعف عام وبطء. بوتاسيوم 7.4، التخطيط: موجات T مدببة ومركب QRS واسع. الضغط 118/72 ولا ألم صدري.",
        guidelineRef = "UK Kidney Association 2023 Hyperkalaemia Guidelines: كالسيوم عند تغيرات تخطيطية + أنسولين مع سكر + سالبوتامول، ثم إزالة البوتاسيوم وعلاج السبب.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "فرط بوتاسيوم شديد مع QRS واسع. ما التدبير الفوري؟",
                vitals = ErVitals(hr = 58, sbp = 118, dbp = 72, rr = 17, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "غلوكونات كالسيوم وريدي + أنسولين مع سكر + سالبوتامول بالرذاذ + مراقبة قلبية", points = 10, feedback = "صحيح. الكالسيوم يثبت الغشاء فورًا، والأنسولين والسالبوتامول ينقلان البوتاسيوم للداخل.", nextNodeId = "n2"),
                    ErAction(label = "راتنج مبادل بوتاسيوم وحده وانتظار", points = -5, feedback = "ناقص وبطيء. الراتنج لا يثبت القلب في تغيرات تخطيطية مهددة.", nextNodeId = "n2"),
                    ErAction(label = "مدر فقط", points = -5, feedback = "ناقص. المدر بطيء ولا يكفي مع QRS واسع.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا لخفض النبض", points = -10, feedback = "خطأ. البطء هنا من فرط البوتاسيوم لا يحتاج حصرًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "ضاق QRS وانخفض البوتاسيوم إلى 6.2. ما التالي؟",
                vitals = ErVitals(hr = 72, sbp = 122, dbp = 76, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "إزالة بوتاسيوم (مدر/راتنج/غسيل عند الحاجة) + إيقاف أدوية رافعة للبوتاسيوم ومراقبة سكر", points = 10, feedback = "صحيح. النقل للداخل مؤقت؛ يجب إزالة البوتاسيوم وعلاج السبب.", nextNodeId = null, endOutcome = "أُنجزت الحالة: فرط بوتاسيوم مهدد عولج بتثبيت ونقل وإزالة."),
                    ErAction(label = "تخريج فور انخفاض البوتاسيوم قليلًا", points = -5, feedback = "خطأ. البوتاسيوم ما زال مرتفعًا والنكس محتمل.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "استئناف مثبط ACE بجرعة عالية فورًا", points = -5, feedback = "خطأ. الأدوية الرافعة للبوتاسيوم تُراجع قبل استئنافها.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: فرط بوتاسيوم مع تغيرات تخطيطية: كالسيوم أولًا ثم نقل للداخل ثم إزالة — الراتنج وحده بطيء جدًا."
    ),
    ErCase(
        id = "a_syncope",
        title = "إغماء غير مشخص بعوامل خطر عالية",
        presentation = "رجل 64 سنة، إغماء مفاجئ بلا إنذار أثناء الجلوس، مع ألم صدري خفيف قبله. التخطيط: حصار فرعي أيسر جديد، ولا إسهال ولا نزف ولا علامات إنتان.",
        guidelineRef = "Brignole et al., 2018 ESC Syncope Guidelines, Eur Heart J 2018;39:1883-1948, doi 10.1093/eurheartj/ehy037: الإغماء بلا إنذار مع تخطيط شاذ أو ألم صدري عالي الخطر — قبول ومراقبة وتقييم قلبي عاجل، لا تخريج سريع.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "إغماء عالي الخطر بتخطيط شاذ. ما التدبير؟",
                vitals = ErVitals(hr = 78, sbp = 126, dbp = 80, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "قبول ومراقبة قلبية + تخطيط مستمر وتروبونين وإيكو وتقييم إقفار واضطراب نظم", points = 10, feedback = "صحيح. الإغماء القلبي قاتل محتمل ويحتاج استبعاد إقفار وحصار واضطراب نظم.", nextNodeId = "n2"),
                    ErAction(label = "تشخيص وعائي مبهمي وتخريج", points = -10, feedback = "خطير. الإغماء بلا إنذار مع تخطيط شاذ ليس مبهميًا حميدًا.", nextNodeId = "n2"),
                    ErAction(label = "صورة دماغ فقط", points = -5, feedback = "ناقص. الإغماء القلبي أهم من التصوير الدماغي الروتيني هنا.", nextNodeId = "n2"),
                    ErAction(label = "سوائل وريدية وتخريج", points = -5, feedback = "ناقص. السوائل لا تستبعد سببًا قلبيًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "المراقبة أظهرت نوبات حصار AV متقطع. ما القرار؟",
                vitals = ErVitals(hr = 52, sbp = 118, dbp = 76, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "تحويل لزرع منظم دائم + منع قيادة حتى التقييم", points = 10, feedback = "صحيح. الحصار المتقطع المسبب للإغماء يستدعي تنظيمًا دائمًا.", nextNodeId = null, endOutcome = "أُنجزت الحالة: إغماء عالي الخطر كُشف سببه وعولج."),
                    ErAction(label = "تخريج مع طمأنة", points = -10, feedback = "خطير. حصار مسبب للإغماء قد ينتهي بتوقف.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج قاتل محتمل."),
                    ErAction(label = "حاصرات بيتا للوقاية", points = -10, feedback = "خطأ. حاصرات التوصيل تفاقم الحصار.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: الإغماء بلا إنذار مع تخطيط شاذ هو قلبي حتى يثبت العكس — قبول ومراقبة لا تخريج."
    ),
    ErCase(
        id = "a_postarrest",
        title = "رعاية ما بعد عودة النبض — Post-Arrest Care",
        presentation = "امرأة 59 سنة بعد VF وتوقف ناجح (ROSC). الآن ضغط 92/60، SpO2 ‏92%، وما زالت فاقدة الوعي. التخطيط بعد ROSC: ارتفاع ST أمامي.",
        guidelineRef = "AHA ACLS 2020 Post-Cardiac Arrest Care و ERC 2021: بعد ROSC، قثطرة عاجلة عند STEMI، منع الحمى، أكسجة متحفظة، وضغط شرياني متوسط فوق 65.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ROSC مع STEMI وفاقد وعي. ما الأولوية؟",
                vitals = ErVitals(hr = 104, sbp = 92, dbp = 60, rr = 14, spo2 = 92, gcs = 6),
                actions = listOf(
                    ErAction(label = "قثطرة طارئة + عناية + منع حمى وأكسجة متحفظة (SpO2 ‏92-98%)", points = 10, feedback = "صحيح. STEMI بعد التوقف يستدعي قثطرة، والحمى وفرط الأكسجة يضران بالدماغ.", nextNodeId = "n2"),
                    ErAction(label = "أكسجين 100% مطول", points = -5, feedback = "خطأ. فرط الأكسجة بعد التوقف يزيد أذية الدماغ؛ الهدف 92-98%.", nextNodeId = "n2"),
                    ErAction(label = "تبريد شديد إلى 30 درجة", points = -5, feedback = "خطأ. الهدف منع الحمى والحفاظ على حرارة طبيعية لا تبريد شديد.", nextNodeId = "n2"),
                    ErAction(label = "تأجيل القثطرة حتى استيقاظ المريضة", points = -10, feedback = "خطير. فقد الوعي ليس مانعًا للقثطرة عند STEMI بعد التوقف.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "تمت القثطرة بنجاح والمريضة في العناية. ما التنبؤ العصبي؟",
                vitals = ErVitals(hr = 88, sbp = 112, dbp = 70, rr = 14, spo2 = 95, gcs = 7),
                actions = listOf(
                    ErAction(label = "انتظار 72 ساعة على الأقل بعد عودة الحرارة مع تقييم متعدد الوسائط قبل أي تنبؤ نهائي", points = 10, feedback = "صحيح. التنبؤ المبكر قبل 72 ساعة يخطئ بسبب التهدئة وانخفاض الحرارة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: رعاية ما بعد التوقف أُديرت بلا تنبؤ مبكر."),
                    ErAction(label = "إعلان إنذار سيئ نهائي خلال 6 ساعات", points = -10, feedback = "خطير. التنبؤ المبكر قد ينهي رعاية مريض قابل للتعافي.", nextNodeId = null, endOutcome = "انتهت الحالة بتنبؤ مبكر خاطئ."),
                    ErAction(label = "إيقاف الدعم لعدم الاستيقاظ السريع", points = -10, feedback = "خطأ. عدم الاستيقاظ المبكر شائع ولا يبرر إيقاف الدعم.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر غير مبرر.")
                )
            )
        ),
        endSummary = "الدرس: بعد ROSC، قثطرة عند STEMI ومنع حمى وأكسجة متحفظة، ولا تنبؤ عصبي نهائي قبل 72 ساعة."
    ),
    ErCase(
        id = "a_takotsubo",
        title = "اعتلال تاكوتسوبو — Takotsubo Cardiomyopathy",
        presentation = "امرأة 66 سنة بعد وفاة قريب، ألم صدري وضيق نفس. التخطيط: تغيرات ST وانعكاس T، تروبونين مرتفع قليلًا، والإيكو: توسع قمي مع قاعدة مفرطة الحركة.",
        guidelineRef = "ESC 2023 Cardiomyopathies و InterTAK: تاكوتسوبو تشخيص بعد نفي انسداد إكليلي بالقثطرة، وعلاجه دعم قصور قلب مع تجنب الحالّ عند نفي الانسداد.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "اشتباه تاكوتسوبو لكن الاحتشاء لم يُنفَ بعد. ما الخطوة؟",
                vitals = ErVitals(hr = 98, sbp = 128, dbp = 80, rr = 20, spo2 = 95, gcs = 15),
                actions = listOf(
                    ErAction(label = "علاج كـ ACS أولًا + قثطرة عاجلة لنفي الانسداد الإكليلي", points = 10, feedback = "صحيح. تاكوتسوبو لا يُشخص قبل نفي الانسداد؛ علاج ACS أولًا آمن.", nextNodeId = "n2"),
                    ErAction(label = "تشخيص تاكوتسوبو سريريًا وتجاهل القثطرة", points = -10, feedback = "خطير. تجاهل القثطرة قد يفوت احتشاءً حقيقيًا.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة فورًا بلا قثطرة", points = -5, feedback = "غير مفضل عند توفر القثطرة؛ الحالّ في تاكوتسوبو بلا انسداد لا يفيد.", nextNodeId = "n2"),
                    ErAction(label = "تخريج لأن التروبونين مرتفع قليلًا فقط", points = -10, feedback = "خطأ. قصور قلبي حاد مع تغيرات تخطيطية يحتاج قبولًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "القثطرة: شرايين نظيفة، والإيكو يؤكد نمط تاكوتسوبو. ما العلاج؟",
                vitals = ErVitals(hr = 92, sbp = 122, dbp = 78, rr = 19, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "علاج قصور قلب داعم + مراقبة اضطراب نظم وخثرة قمية + دعم نفسي", points = 10, feedback = "صحيح. تاكوتسوبو يتعافى غالبًا خلال أسابيع بالدعم ومراقبة المضاعفات.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تاكوتسوبو شُخص بعد نفي الانسداد وعولج داعمًا."),
                    ErAction(label = "استمرار حالّ ومضاد تخثر قوي طويلًا", points = -5, feedback = "خطأ. بلا انسداد، الحالّ والمضاد القوي لا يفيدان.", nextNodeId = null, endOutcome = "انتهت الحالة بعلاج غير مبرر."),
                    ErAction(label = "إخبار المريضة أنه احتشاء دائم بلا تعافٍ", points = -5, feedback = "خطأ. تاكوتسوبو يتعافى غالبًا والتشخيص الدقيق يغير الإنذار.", nextNodeId = null, endOutcome = "انتهت الحالة بتشخيص خاطئ.")
                )
            )
        ),
        endSummary = "الدرس: تاكوتسوبو يُشخص بعد نفي الانسداد بالقثطرة، وعلاجه داعم مع مراقبة المضاعفات."
    ),
    ErCase(
        id = "a_papillary_rupture",
        title = "تمزق عضلة حليمية — Acute Severe MR",
        presentation = "رجل 64 سنة بعد احتشاء سفلي بأيام، ضيق نفس مفاجئ شديد مع نفخة انقباضية جديدة عالية. الضغط 98/60، وذمة رئوية حادة، والإيكو: قلس ميترالي شديد مع وريقة متدلية.",
        guidelineRef = "ESC 2023 ACS و ESC 2021 Valvular Heart Disease: التمزق الحليمي طارئ جراحي — دعم بموسع ومضخة بالون/دعم ميكانيكي كجسر لجراحة صمام عاجلة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "قلس ميترالي حاد شديد بعد احتشاء مع وذمة رئوية. ما التدبير؟",
                vitals = ErVitals(hr = 118, sbp = 98, dbp = 60, rr = 28, spo2 = 89, gcs = 14),
                actions = listOf(
                    ErAction(label = "دعم (نيتروبروسيد/دوبوتامين + بالون داخل أبهري عند الحاجة) + جراحة قلب طارئة", points = 10, feedback = "صحيح. خفض ما بعد الحمل والدعم جسر، لكن الجراحة هي العلاج الحاسم.", nextNodeId = "n2"),
                    ErAction(label = "مدر قوي وحده", points = -5, feedback = "ناقص. المدر لا يصلح قلسًا ميكانيكيًا شديدًا.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة كعلاج وحيد", points = -10, feedback = "خطأ. الحالّ لا يصلح تمزقًا ميكانيكيًا وقد يؤخر الجراحة.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا وريدية", points = -10, feedback = "خطير. حاصرات بيتا تثبط التقلص في قصور حاد مع قلس شديد.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "استقر الضغط بدعم والجراحة مجدولة خلال ساعات. ما المتابعة؟",
                vitals = ErVitals(hr = 108, sbp = 104, dbp = 64, rr = 24, spo2 = 92, gcs = 14),
                actions = listOf(
                    ErAction(label = "عناية مركزة قلبية + مراقبة غازية واستمرار الدعم حتى الجراحة", points = 10, feedback = "صحيح. التمزق الحليمي لا ينتظر تحسنًا تلقائيًا؛ الدعم حتى الجراحة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تمزق حليمي أُدير كجسر لجراحة طارئة."),
                    ErAction(label = "تأجيل الجراحة لأسابيع", points = -10, feedback = "قاتل. التأجيل في قلس حاد شديد ينتهي بصدمة.", nextNodeId = null, endOutcome = "انتهت الحالة بتأجيل قاتل."),
                    ErAction(label = "تخريج للجناح العادي", points = -10, feedback = "خطير. حالة عناية مركزة قلبية.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ في مستوى الرعاية.")
                )
            )
        ),
        endSummary = "الدرس: التمزق الحليمي قلس ميكانيكي حاد: دعم كجسر وجراحة طارئة، لا مدر وحده ولا حالّ."
    ),
    ErCase(
        id = "a_vsd_rupture",
        title = "تمزق حاجز بين بطينين بعد احتشاء — Post-MI VSD",
        presentation = "رجل 70 سنة بعد احتشاء أمامي بأيام، تدهور مفاجئ مع نفخة انقباضية جديدة قاسية، ضغط 92/58، وذمة رئوية، والإيكو: عيب حاجز بطيني جديد مع تحويل يسار-يمين.",
        guidelineRef = "ESC 2023 ACS: تمزق الحاجز بعد الاحتشاء طارئ — خفض ما بعد الحمل ودعم ميكانيكي كجسر لإغلاق جراحي/تداخلي، مع توقيت يقرره فريق القلب.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "عيب حاجز جديد بعد احتشاء مع صدمة وشيكة. ما التدبير؟",
                vitals = ErVitals(hr = 116, sbp = 92, dbp = 58, rr = 26, spo2 = 90, gcs = 13),
                actions = listOf(
                    ErAction(label = "خفض ما بعد الحمل + دعم ميكانيكي (بالون/دعم) + فريق قلب لجراحة/إغلاق عاجل", points = 10, feedback = "صحيح. خفض ما بعد الحمل يقلل التحويل، والدعم جسر حتى الإغلاق.", nextNodeId = "n2"),
                    ErAction(label = "مقبضات وعائية قوية وحدها لرفع الضغط", points = -5, feedback = "خطأ. المقبضات تزيد ما بعد الحمل وتفاقم التحويل.", nextNodeId = "n2"),
                    ErAction(label = "سوائل غزيرة", points = -10, feedback = "خطير. السوائل تفاقم الاحتقان والتحويل.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة كعلاج وحيد", points = -10, feedback = "خطأ. الحالّ لا يغلق عيبًا ميكانيكيًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "استقر الضغط بدعم والفريق يخطط للإغلاق. ما المتابعة؟",
                vitals = ErVitals(hr = 106, sbp = 100, dbp = 62, rr = 22, spo2 = 93, gcs = 14),
                actions = listOf(
                    ErAction(label = "عناية مركزة + مراقبة غازية وتقييم متكرر للتوقيت الجراحي", points = 10, feedback = "صحيح. توقيت الإغلاق قرار فريق متخصص يوازن الاستقرار وهشاشة النسيج.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تمزق حاجز أُدير بدعم وإغلاق مخطط."),
                    ErAction(label = "تخريج وانتظار شفاء تلقائي", points = -10, feedback = "قاتل. العيب لا يشفى تلقائيًا.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ قاتل."),
                    ErAction(label = "إيقاف الدعم فور تحسن طفيف", points = -5, feedback = "خطأ. الدعم يستمر حتى الإغلاق.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر.")
                )
            )
        ),
        endSummary = "الدرس: تمزق الحاجز بعد الاحتشاء: خفض ما بعد الحمل ودعم ميكانيكي ثم إغلاق، لا مقبضات قوية وحدها."
    ),
    ErCase(
        id = "a_rv_infarct",
        title = "احتشاء بطين أيمن مع هبوط ضغط",
        presentation = "رجل 58 سنة، احتشاء سفلي مع ارتفاع ST في V4R. ضغط 84/55، أوردة رقبية منتفخة، رئتان صافيتان، وبول قليل. لا وذمة رئوية.",
        guidelineRef = "ESC 2023 ACS: احتشاء البطين الأيمن يعتمد على ما قبل الحمل — سوائل بحذر وتجنب النترات والمدرات، مع إعادة تروية ومقويات عند الحاجة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "احتشاء بطين أيمن مع هبوط ضغط ورئتين صافيتين. ما التدبير؟",
                vitals = ErVitals(hr = 98, sbp = 84, dbp = 55, rr = 20, spo2 = 94, gcs = 14),
                actions = listOf(
                    ErAction(label = "سوائل وريدية بحذر (دفعات 250 مل مع إعادة تقييم) + إعادة تروية + تجنب النترات والمدرات", points = 10, feedback = "صحيح. البطين الأيمن يحتاج ما قبل الحمل؛ النترات والمدرات تنهار به الضغط.", nextNodeId = "n2"),
                    ErAction(label = "نترات وريدية قوية لألم الصدر", points = -10, feedback = "خطير. النترات في احتشاء أيمن تسبب انهيار ضغط.", nextNodeId = "n2"),
                    ErAction(label = "مدر قوي للاحتقان الوريدي", points = -10, feedback = "خطأ. الاحتقان الوريدي هنا من فشل أيمن لا من فرط حجم؛ المدر ينهار الضغط.", nextNodeId = "n2"),
                    ErAction(label = "سوائل غزيرة بلا إعادة تقييم", points = -5, feedback = "خطأ. السوائل بحذر مع إعادة تقييم، لا غزيرة عمياء.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "ارتفع الضغط إلى 104/66 بعد دفعات حذرة وإعادة تروية. ما المتابعة؟",
                vitals = ErVitals(hr = 88, sbp = 104, dbp = 66, rr = 18, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة + دوبوتامين عند استمرار نقص النتاج + علاج ACS الكامل", points = 10, feedback = "صحيح. عند فشل السوائل الحذرة، المقوي هو الخط التالي مع إعادة التروية.", nextNodeId = null, endOutcome = "أُنجزت الحالة: احتشاء أيمن أُدير بسوائل حذرة وإعادة تروية."),
                    ErAction(label = "إعطاء نترات الآن بعد تحسن الضغط", points = -5, feedback = "خطأ. النترات تبقى محفوفة بالخطر في احتشاء أيمن.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي."),
                    ErAction(label = "تخريج فوري", points = -5, feedback = "خطأ. احتشاء أيمن مع هبوط ضغط يحتاج مراقبة.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر.")
                )
            )
        ),
        endSummary = "الدرس: في احتشاء البطين الأيمن، سوائل بحذر ولا نترات ولا مدرات — البطين الأيمن يعيش على ما قبل الحمل."
    ),
    ErCase(
        id = "a_vasospastic",
        title = "ذبحة تشنجية وعائية — Prinzmetal Angina",
        presentation = "امرأة 46 سنة مدخنة، ألم صدري ليلي متكرر يستيقظها، يزول بالنتروغليسرين. التخطيط أثناء الألم: ارتفاع ST عابر يزول بعده، والقثطرة بين النوبات طبيعية نسبيًا.",
        guidelineRef = "ESC 2023 ACS و ESC 2024 Chronic Coronary Syndromes: الذبحة التشنجية تُعالج بحاصرات قنوات الكالسيوم + نترات، مع إيقاف التدخين وتجنب حاصرات بيتا وحيدة.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ذبحة تشنجية مشتبهة مع تخطيط طبيعي بين النوبات. ما العلاج الوقائي؟",
                vitals = ErVitals(hr = 82, sbp = 128, dbp = 80, rr = 17, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "حاصرات قنوات كالسيوم + نترات عند الحاجة + إيقاف تدخين", points = 10, feedback = "صحيح. حاصرات الكالسيوم تمنع التشنج، والنترات تعالجه، وإيقاف التدخين أساسي.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا وحيدة كوقاية", points = -10, feedback = "خطير. حاصرات بيتا وحيدة قد تفاقم التشنج بحصر بيتا وترك ألفا.", nextNodeId = "n2"),
                    ErAction(label = "دعامة روتينية رغم شرايين طبيعية", points = -5, feedback = "خطأ. بلا تضيق ثابت، الدعامة لا تعالج التشنج.", nextNodeId = "n2"),
                    ErAction(label = "تجاهل الألم لأنه ليلي وعابر", points = -10, feedback = "خطأ. التشنج قد يسبب احتشاءً واضطراب نظم.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "قلّت النوبات بعد العلاج. ما المتابعة؟",
                vitals = ErVitals(hr = 78, sbp = 124, dbp = 78, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "استمرار حاصرات الكالسيوم + تجنب محرضات (تدخين، تريبتانات، إرغوت) + متابعة قلبية", points = 10, feedback = "صحيح. تجنب المحرضات جزء من العلاج كالدواء.", nextNodeId = null, endOutcome = "أُنجزت الحالة: ذبحة تشنجية عولجت ووقيت."),
                    ErAction(label = "إيقاف حاصرات الكالسيوم عند أول تحسن", points = -5, feedback = "خطأ. الإيقاف يعيد التشنج.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر."),
                    ErAction(label = "وصف تريبتان للصداع بلا تحذير", points = -5, feedback = "خطأ. التريبتانات مقبضات وعائية قد تحرض التشنج.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: الذبحة التشنجية: حاصرات كالسيوم ونترات وإيقاف تدخين، ولا حاصرات بيتا وحيدة."
    ),
    ErCase(
        id = "a_cocaine_chest",
        title = "ألم صدري مرتبط بالكوكايين",
        presentation = "شاب 29 سنة بعد تعاطي كوكايين بساعتين، ألم صدري ضاغط، قلق وارتعاش. ضغط 172/105، نبض 128، التخطيط: تغيرات ST غير نوعية، والتروبونين حدودي.",
        guidelineRef = "AHA 2023 ACS / Cocaine-Associated Chest Pain Scientific Statement: بنزوديازيبين + نترات وأسبرين، وتجنب حاصرات بيتا وحيدة في التسمم الحاد بالكوكايين.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "ألم صدري بعد كوكايين مع ارتفاع ضغط وتسرع. ما التدبير؟",
                vitals = ErVitals(hr = 128, sbp = 172, dbp = 105, rr = 20, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "بنزوديازيبين + نترات + أسبرين + مراقبة وتصوير إكليلي عند معايير احتشاء", points = 10, feedback = "صحيح. البنزوديازيبين يهدئ الدفع الودي، والنترات تعالج التشنج، وحاصرات بيتا وحيدة ممنوعة.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات بيتا وريدية فورًا لخفض النبض والضغط", points = -10, feedback = "خطير. حصر بيتا وحيدًا يترك ألفا ويزيد التشنج والضغط.", nextNodeId = "n2"),
                    ErAction(label = "حالّ للخثرة فورًا بلا قثطرة", points = -5, feedback = "غير مفضل. القثطرة أولى عند توفرها؛ التشنج شائع والتقييم الغازي يفرق.", nextNodeId = "n2"),
                    ErAction(label = "تخريج لأن التروبونين حدودي", points = -10, feedback = "خطأ. ألم بعد كوكايين يحتاج مراقبة وتقييمًا كاملًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "هدأ الألم وانخفض الضغط إلى 138/88 بعد البنزوديازيبين والنترات. ما المتابعة؟",
                vitals = ErVitals(hr = 96, sbp = 138, dbp = 88, rr = 18, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة 12-24 ساعة + تثقيف بإيقاف الكوكايين وتحويل لدعم إدمان", points = 10, feedback = "صحيح. المراقبة تلتقط احتشاءً متأخرًا، وعلاج الإدمان يمنع النكس.", nextNodeId = null, endOutcome = "أُنجزت الحالة: ألم كوكايين أُدير بلا حاصرات بيتا وحيدة."),
                    ErAction(label = "تخريج فوري بعد هدوء الألم", points = -5, feedback = "خطأ. المراقبة مطلوبة بعد ألم كوكايين.", nextNodeId = null, endOutcome = "انتهت الحالة بتخريج مبكر."),
                    ErAction(label = "وصف حاصرات بيتا للمنزل فورًا", points = -5, feedback = "خطأ. حاصرات بيتا بعد الكوكايين الحاد تحتاج حذرًا وتقييمًا متخصصًا.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: ألم الكوكايين: بنزوديازيبين ونترات، ولا حاصرات بيتا وحيدة في التسمم الحاد."
    ),
    ErCase(
        id = "a_critical_as",
        title = "تضيق أبهري حرج متدهور — Critical AS",
        presentation = "رجل 81 سنة بتضيق أبهري شديد معروف، إغماء متكرر وضيق نفس، نفخة انقباضية قاسية، ضغط 102/70، ورئتان محتقنتان خفيفًا. الإيكو: مساحة صمام 0.6 سم مربع.",
        guidelineRef = "ESC 2021 Valvular Heart Disease: التضيق الأبهري الحرج العرضي علاجُه استبدال الصمام (جراحي/TAVI)؛ الموسعات والمدرات القوية خطرة قبل الاستبدال.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تضيق أبهري حرج عرضي مع ضغط حدودي. ما التدبير؟",
                vitals = ErVitals(hr = 88, sbp = 102, dbp = 70, rr = 22, spo2 = 93, gcs = 15),
                actions = listOf(
                    ErAction(label = "قبول وعناية + فريق صمامات لاستبدال عاجل (TAVI/جراحة) مع دعم حذر", points = 10, feedback = "صحيح. الاستبدال هو العلاج الحاسم؛ الأدوية لا تفتح صمامًا متكلسًا حرجًا.", nextNodeId = "n2"),
                    ErAction(label = "نترات قوية ومدر قوي لضيق النفس", points = -10, feedback = "خطير. خفض ما قبل الحمل في تضيق حرج ينهار به النتاج.", nextNodeId = "n2"),
                    ErAction(label = "موسع وعائي قوي لخفض الضغط", points = -10, feedback = "خطير. الموسعات في تضيق حرج تسبب انهيار ضغط.", nextNodeId = "n2"),
                    ErAction(label = "تخريج وموعد روتيني بعد أشهر", points = -10, feedback = "خطير. التضيق الحرج العرضي وفياتُه عالية بلا استبدال قريب.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "الفريق قرر TAVI قريبًا والمريض مستقر نسبيًا. ما المتابعة؟",
                vitals = ErVitals(hr = 84, sbp = 108, dbp = 72, rr = 20, spo2 = 95, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة لصيقة + تجنب هبوط ضغط + تجهيز TAVI/جراحة دون تأخير", points = 10, feedback = "صحيح. الاستقرار هش والتأخير يعيد الإغماء أو الوذمة.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تضيق أبهري حرج أُحيل لاستبدال عاجل."),
                    ErAction(label = "إعطاء مدر قوي لتحسين التنفس قبل الإجراء", points = -5, feedback = "خطأ. المدر القوي ينهار به ما قبل الحمل في تضيق حرج.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي."),
                    ErAction(label = "تأجيل الاستبدال حتى تدهور أكبر", points = -10, feedback = "خطأ. الانتظار في تضيق حرج عرضي قاتل.", nextNodeId = null, endOutcome = "انتهت الحالة بتأجيل قاتل.")
                )
            )
        ),
        endSummary = "الدرس: التضيق الأبهري الحرج العرضي علاجُه الاستبدال، والموسعات والمدرات القوية خطرة قبله."
    ),
    ErCase(
        id = "a_hcm_syncope",
        title = "اعتلال عضلة ضخامي مع إغماء جهدي — HCM Syncope",
        presentation = "شاب 21 سنة، إغماء أثناء مباراة كرة قدم، بلا إنذار. الإيكو: تضخم حاجز 22 ملم مع انسداد مخرج، والتخطيط: تضخم بطيني أيسر وموجات T مقلوبة عميقة. لا قصة عائلية موت مفاجئ معروفة بعد.",
        guidelineRef = "ESC 2023 Cardiomyopathies: الإغماء الجهدي في HCM علامة خطر موت مفاجئ — قبول ومراقبة وتقييم ICD، مع منع جهد تنافسي حتى التقييم.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "إغماء جهدي مع HCM. ما التدبير؟",
                vitals = ErVitals(hr = 82, sbp = 118, dbp = 74, rr = 17, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "قبول ومراقبة + تقييم خطر موت مفاجئ وICD + منع جهد تنافسي", points = 10, feedback = "صحيح. الإغماء الجهدي في HCM ليس حميدًا ويستدعي تقييم ICD عاجلًا.", nextNodeId = "n2"),
                    ErAction(label = "تشخيص جفاف وتخريج بسوائل", points = -10, feedback = "خطير. إغماء جهدي مع HCM لا يُفسَّر بالجفاف.", nextNodeId = "n2"),
                    ErAction(label = "موسعات وعائية لتحسين المخرج", points = -10, feedback = "خطأ. الموسعات تفاقم انسداد المخرج في HCM.", nextNodeId = "n2"),
                    ErAction(label = "عودة للرياضة التنافسية فورًا", points = -10, feedback = "قاتل محتمل. الجهد التنافسي قبل تقييم ICD محفوف بالموت المفاجئ.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "التقييم أظهر خطرًا عاليًا وقُرر زرع ICD. ما المتابعة؟",
                vitals = ErVitals(hr = 78, sbp = 120, dbp = 76, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "زرع ICD + فحص عائلي + حاصرات بيتا/فيراباميل حسب الانسداد + منع جهد تنافسي", points = 10, feedback = "صحيح. الفحص العائلي يكشف أقاربًا معرضين، وICD يحمي من الموت المفاجئ.", nextNodeId = null, endOutcome = "أُنجزت الحالة: إغماء HCM قُيم خطرُه وحُمي بـ ICD."),
                    ErAction(label = "تخريج بلا ICD ولا فحص عائلي", points = -10, feedback = "خطير. الخطر يبقى على المريض وعائلته.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة خطيرة."),
                    ErAction(label = "مدر قوي لضيق النفس", points = -5, feedback = "خطأ. المدر القوي يفاقم انسداد المخرج.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ دوائي.")
                )
            )
        ),
        endSummary = "الدرس: الإغماء الجهدي في HCM علامة موت مفاجئ — تقييم ICD ومنع جهد تنافسي، لا موسعات ولا عودة للملعب."
    ),
    ErCase(
        id = "a_carotid_dissection",
        title = "تسلخ شريان سباتي رقبي — Carotid Dissection",
        presentation = "امرأة 41 سنة، ألم رقبي وحيد الجانب بعد حركة رقبة مفاجئة، مع صداع ومتلازمة هورنر جزئية وضعف وجهي خفيف عابر. التصوير الوعائي: تضيق وتسلخ بالسباتي الداخلي.",
        guidelineRef = "AHA/ASA Scientific Statement on Cervical Artery Dissection: العلاج مضاد صفيحات أو مضاد تخثر لمنع السكتة، مع ضبط ضغط وتجنب التلاعب بالرقبة، والتداخل عند الإقفار المتكرر.",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "تسلخ سباتي مع أعراض إقفارية عابرة. ما التدبير؟",
                vitals = ErVitals(hr = 86, sbp = 138, dbp = 84, rr = 17, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "مضاد صفيحات أو مضاد تخثر لمنع السكتة + ضبط ضغط + تجنب تلاعب رقبي", points = 10, feedback = "صحيح. منع الخثار على جدار التسلخ هو أساس الوقاية من السكتة.", nextNodeId = "n2"),
                    ErAction(label = "علاج فيزيائي رقبي قوي وتدليك", points = -10, feedback = "خطير. التلاعب بالرقبة قد يمدد التسلخ ويسبب سكتة.", nextNodeId = "n2"),
                    ErAction(label = "مسكن فقط وتجاهل التسلخ", points = -10, feedback = "خطأ. التسلخ بلا وقاية خثارية ينتهي بسكتة.", nextNodeId = "n2"),
                    ErAction(label = "دعامة فورية روتينية لكل تسلخ", points = -5, feedback = "غير ضروري روتينًا. الدعامة تُحفظ للإقفار المتكرر رغم العلاج الطبي.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "استقرت الأعراض على العلاج الطبي. ما المتابعة؟",
                vitals = ErVitals(hr = 82, sbp = 132, dbp = 80, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "متابعة وعائية وتصوير متكرر + علاج 3-6 أشهر ثم إعادة تقييم", points = 10, feedback = "صحيح. التسلخ يلتئم غالبًا خلال أشهر والعلاج يُراجع بالتصوير.", nextNodeId = null, endOutcome = "أُنجزت الحالة: تسلخ سباتي عولج طبيًا مع متابعة وعائية."),
                    ErAction(label = "إيقاف الوقاية الخثارية فور زوال الألم", points = -5, feedback = "خطأ. زوال الألم لا يعني التئام التسلخ.", nextNodeId = null, endOutcome = "انتهت الحالة بإيقاف مبكر."),
                    ErAction(label = "تخريج بلا متابعة تصويرية", points = -5, feedback = "ناقص. التصوير المتكرر يقرر مدة العلاج.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة.")
                )
            )
        ),
        endSummary = "الدرس: تسلخ السباتي: وقاية خثارية وضبط ضغط ولا تلاعب رقبي، والدعامة للإقفار المتكرر فقط."
    ),
    ErCase(
        id = "a_hit_thrombosis",
        title = "نقص صفيحات محرض بالهيبارين مع خثار — HIT",
        presentation = "رجل 63 سنة على هيبارين منذ 8 أيام، صفيحات هبطت من 240 إلى 95 ألف، مع خثار وريدي جديد بالساق وألم صدري خفيف. لا نزف ظاهر.",
        guidelineRef = "Cuker et al., ASH 2018 HIT Guidelines, Blood Adv 2018;2:3360-3392, doi 10.1182/bloodadvances.2018024489: أوقف كل الهيبارين فورًا عند الاشتباه العالي، ولا تنقل صفيحات، وابدأ مضاد تخثر غير هيباريني (أرغاتروبان/فوندابارينوكس).",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "اشتباه HIT عالي مع خثار جديد. ما التدبير الفوري؟",
                vitals = ErVitals(hr = 104, sbp = 128, dbp = 80, rr = 19, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(label = "إيقاف كل الهيبارين فورًا + بدء مضاد غير هيباريني + طلب أضداد PF4", points = 10, feedback = "صحيح. استمرار الهيبارين في HIT يفاقم الخثار، والمضاد البديل يمنع خثارًا جديدًا.", nextNodeId = "n2"),
                    ErAction(label = "استمرار الهيبارين حتى نتيجة الأضداد", points = -10, feedback = "خطير. الانتظار في HIT عالي الاشتباه يسمح بخثار قاتل.", nextNodeId = "n2"),
                    ErAction(label = "نقل صفيحات لرفع العدد", points = -10, feedback = "خطير. نقل الصفيحات في HIT يغذي الخثار.", nextNodeId = "n2"),
                    ErAction(label = "بدء وارفارين وحده فورًا", points = -10, feedback = "خطير. الوارفارين وحده في HIT الحاد يسبب نخرًا جلديًا وخثارًا.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "أُوقف الهيبارين وبُدئ البديل. ما المتابعة؟",
                vitals = ErVitals(hr = 98, sbp = 126, dbp = 78, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(label = "مراقبة صفيحات وخثار + تحويل لوارفارين فقط بعد تعافي الصفيحات + توثيق تحسس هيبارين", points = 10, feedback = "صحيح. الوارفارين يُضاف بعد تعافي الصفيحات، وتوثيق HIT يمنع إعادة التعرض.", nextNodeId = null, endOutcome = "أُنجزت الحالة: HIT أُدير بإيقاف هيبارين ومضاد بديل."),
                    ErAction(label = "إعادة هيبارين بعد تحسن الصفيحات قليلًا", points = -10, feedback = "خطير. إعادة الهيبارين في HIT تعيد الخثار.", nextNodeId = null, endOutcome = "انتهت الحالة بإعادة تعرض خطيرة."),
                    ErAction(label = "تخريج بلا مضاد بديل", points = -10, feedback = "خطير. HIT حالة خثارية نشطة تحتاج مضادًا بديلًا.", nextNodeId = null, endOutcome = "انتهت الحالة بخطأ خطير.")
                )
            )
        ),
        endSummary = "الدرس: في HIT، أوقف كل الهيبارين فورًا ولا تنقل صفيحات ولا تبدأ وارفارين وحده — مضاد غير هيباريني."
    ),
    ErCase(
        id = "a_brugada",
        title = "متلازمة بروغادا مع إغماء — Brugada Syndrome",
        presentation = "رجل 38 سنة، إغماء ليلي بلا إنذار، قصة عائلية موت مفاجئ لأخ. التخطيط: نمط بروغادا نوع 1 في V1-V2، ولا إقفار ولا اضطراب كهارل. حرارة طبيعية الآن.",
        guidelineRef = "Zeppenfeld et al., 2022 ESC Ventricular Arrhythmias Guidelines, Eur Heart J 2022;43:3997-4126, doi 10.1093/eurheartj/ehac262: بروغادا مع إغماء محتمل اضطراب نظمي يستدعي تقييم ICD، مع تجنب محرضات (حمى، حاصرات صوديوم، كحول زائد).",
        startNodeId = "n1",
        nodes = listOf(
            ErNode(
                id = "n1",
                text = "بروغادا نوع 1 مع إغماء وقصة عائلية موت مفاجئ. ما التدبير؟",
                vitals = ErVitals(hr = 74, sbp = 122, dbp = 78, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "قبول ومراقبة + تقييم ICD عاجل + علاج حمى فورًا عند حدوثها وتجنب محرضات", points = 10, feedback = "صحيح. الإغماء في بروغادا علامة خطر، والحمى محرض معروف يجب علاجه فورًا.", nextNodeId = "n2"),
                    ErAction(label = "تشخيص قلق وتخريج", points = -10, feedback = "خطير. نمط نوع 1 مع إغماء وقصة عائلية ليس قلقًا.", nextNodeId = "n2"),
                    ErAction(label = "حاصرات صوديوم لعلاج التخطيط", points = -10, feedback = "خطير. حاصرات الصوديوم تكشف النمط وتفاقم الخطر في بروغادا.", nextNodeId = "n2"),
                    ErAction(label = "مضاد تخثر كعلاج", points = -5, feedback = "خطأ. بروغادا اضطراب نظم كهربائي لا خثاري.", nextNodeId = "n2")
                )
            ),
            ErNode(
                id = "n2",
                text = "قُرر زرع ICD والمراقبة سليمة. ما المتابعة؟",
                vitals = ErVitals(hr = 72, sbp = 120, dbp = 76, rr = 16, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(label = "زرع ICD + فحص عائلي + قائمة أدوية ممنوعة وتثقيف بعلاج الحمى فورًا", points = 10, feedback = "صحيح. الفحص العائلي ينقذ أقاربًا، وقائمة الأدوية الممنوعة تمنع تحريض النمط.", nextNodeId = null, endOutcome = "أُنجزت الحالة: بروغادا قُيم خطرُها وحُميت بـ ICD."),
                    ErAction(label = "تخريج بلا ICD ولا فحص عائلي", points = -10, feedback = "خطير. الخطر يبقى على المريض وعائلته.", nextNodeId = null, endOutcome = "انتهت الحالة بخطة ناقصة خطيرة."),
                    ErAction(label = "تجاهل الحمى مستقبلًا", points = -5, feedback = "خطأ. الحمى محرض ويجب علاجها فورًا في بروغادا.", nextNodeId = null, endOutcome = "انتهت الحالة بتثقيف ناقص.")
                )
            )
        ),
        endSummary = "الدرس: بروغادا مع إغماء: تقييم ICD وفحص عائلي وعلاج حمى فوري وتجنب حاصرات الصوديوم."
    )
)
