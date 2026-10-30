package sy.medpro.watani.ui.features

val erCasesWaveC: List<ErCase> = listOf(
    ErCase(
        id = "c_copd_exacerbation",
        title = "تفاقم داء انسداد رئوي مزمن — COPD",
        presentation = "رجل 68 سنة، مدخن سابق، COPD معروف، ضيق نفس متزايد منذ يومين مع سعال منتج لبلغم قيحي أكثر، أزيز منتشر، ولا ألم صدري.",
        guidelineRef = "GOLD 2024: أكسجين بهدف SpO2 بين 88-92%، موسعات قصبية قصيرة المفعول، ستيرويد جهازي، ومضاد حيوي عند زيادة القيحية، وتهوية غير باضعة عند الحماض التنفسي.",
        startNodeId = "c_copd_exacerbation_n1",
        nodes = listOf(
            ErNode(
                id = "c_copd_exacerbation_n1",
                text = "وصل بضائقة تنفسية وSpO2 ‏84% على الهواء. ما أول تدبير؟",
                vitals = ErVitals(hr = 112, sbp = 138, dbp = 82, rr = 28, spo2 = 84, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أكسجين مضبوط بهدف 88-92% + سالبوتامول وإبراتروبيوم بالرذاذ",
                        points = 10,
                        feedback = "صحيح. هدف 88-92% يمنع تفاقم فرط ثنائي أكسيد الكربون، والموسعات القصيرة هي أساس العلاج.",
                        nextNodeId = "c_copd_exacerbation_n2"
                    ),
                    ErAction(
                        label = "أكسجين عالي التدفق بلا هدف حتى SpO2 ‏100%",
                        points = -10,
                        feedback = "خطير. الأكسجين المفرط في COPD يفاقم احتباس CO2 وقد يسبب حماضًا وتثبيطًا تنفسيًا.",
                        nextNodeId = "c_copd_exacerbation_n2"
                    ),
                    ErAction(
                        label = "صادات وريدية فقط بلا موسعات ولا أكسجين مضبوط",
                        points = -5,
                        feedback = "ناقص. الصادات لا تفتح القصبات ولا تصحح نقص الأكسجة؛ الموسعات والأكسجين أولًا.",
                        nextNodeId = "c_copd_exacerbation_n2"
                    ),
                    ErAction(
                        label = "تنبيب فوري قبل أي علاج دوائي",
                        points = -5,
                        feedback = "غير صحيح كخطوة أولى. التنبيب يُحفظ لفشل العلاج الطبي أو الإعياء أو توقف التنفس.",
                        nextNodeId = "c_copd_exacerbation_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_copd_exacerbation_n2",
                text = "غازات الدم: pH ‏7.30، PaCO2 ‏68، PaO2 ‏58 على الأكسجين. البلغم قيحي. ما التالي؟",
                vitals = ErVitals(hr = 118, sbp = 142, dbp = 84, rr = 26, spo2 = 90, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "بريدنيزولون جهازي + مضاد حيوي + تهوية غير باضعة NIV",
                        points = 10,
                        feedback = "صحيح. الحماض التنفسي مع فرط CO2 هو استطباب NIV، والستيرويد والمضاد الحيوي عند القيحية يقللان مدة التفاقم.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تفاقم COPD عولج بأكسجين مضبوط وموسعات وستيرويد ومضاد حيوي وNIV."
                    ),
                    ErAction(
                        label = "زيادة الأكسجين فقط وانتظار تحسن الغازات",
                        points = -10,
                        feedback = "خطأ. الحماض التنفسي المستمر يحتاج دعمًا تهويويًا، والأكسجين وحده قد يفاقم احتباس CO2.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخطأ: الحماض التنفسي لم يُدعم بـNIV."
                    ),
                    ErAction(
                        label = "ستيرويد جهازي فقط بلا مضاد حيوي ولا NIV",
                        points = -5,
                        feedback = "ناقص. القيحية المتزايدة تستدعي مضادًا حيويًا، والحماض يستدعي NIV.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بخطة ناقصة: غاب المضاد الحيوي وNIV."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تفاقم COPD، الأكسجين بهدف 88-92% لا الإفراط به، مع موسعات وستيرويد، وNIV عند حماض فرط ثنائي أكسيد الكربون."
    ),
    ErCase(
        id = "c_severe_pneumonia",
        title = "ذات رئة مجتمعية شديدة",
        presentation = "امرأة 71 سنة، حرارة 39.5، سعال منتج، ضيق نفس، ارتباك جديد، ضغط 92/58، تنفس 30/د، SpO2 ‏89%. صورة الصدر: ارتشاح فصي أيمن.",
        guidelineRef = "ATS/IDSA 2019 لذات الرئة المجتمعية: تقييم الشدة، صادات واسعة مبكرة (بيتا لاكتام + ماكروليد أو فلوروكينولون تنفسي)، وأكسجين ودعم، وقبول العناية عند الشدة.",
        startNodeId = "c_severe_pneumonia_n1",
        nodes = listOf(
            ErNode(
                id = "c_severe_pneumonia_n1",
                text = "CURB-65 مرتفع مع ارتباك ونقص أكسجة. ما أول خطوة؟",
                vitals = ErVitals(hr = 124, sbp = 92, dbp = 58, rr = 30, spo2 = 89, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "أكسجين + مزارع ثم صادات واسعة فورًا (بيتا لاكتام + ماكروليد)",
                        points = 10,
                        feedback = "صحيح. الصادات المبكرة الواسعة مع الأكسجين هي ما يخفض الوفيات في ذات الرئة الشديدة.",
                        nextNodeId = "c_severe_pneumonia_n2"
                    ),
                    ErAction(
                        label = "انتظار نتائج الزرع قبل إعطاء أي مضاد حيوي",
                        points = -10,
                        feedback = "خطأ. الزرع يُؤخذ أولًا لكن لا يُنتظر؛ كل تأخير يرفع الوفيات.",
                        nextNodeId = "c_severe_pneumonia_n2"
                    ),
                    ErAction(
                        label = "مضاد حيوي فموي وحيد وتخريج للمنزل",
                        points = -10,
                        feedback = "خطير. ذات رئة شديدة مع ارتباك ونقص أكسجة تحتاج قبولًا وعلاجًا وريديًا واسعًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج غير آمن لمريضة بذات رئة شديدة."
                    ),
                    ErAction(
                        label = "خافض حرارة فقط ومراقبة",
                        points = -5,
                        feedback = "ناقص. خافض الحرارة لا يعالج الإنتان الرئوي ولا نقص الأكسجة.",
                        nextNodeId = "c_severe_pneumonia_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_severe_pneumonia_n2",
                text = "بعد الصادات والأكسجين، SpO2 ‏93% لكنها ما زالت بضائقة وبحاجة لأكسجين عالٍ. ما التالي؟",
                vitals = ErVitals(hr = 116, sbp = 100, dbp = 62, rr = 26, spo2 = 93, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "قبول العناية المركزة + استمرار الصادات والأكسجين ومراقبة لصيقة",
                        points = 10,
                        feedback = "صحيح. الحاجة المستمرة لأكسجين عالٍ مع شدة أولية تستدعي العناية.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: ذات رئة شديدة عولجت مبكرًا ثم قُبلت بالعناية."
                    ),
                    ErAction(
                        label = "تخريج لأن الحرارة بدأت تنخفض",
                        points = -10,
                        feedback = "خطأ. انخفاض الحرارة وحده لا يعني استقرارًا تنفسيًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج مبكر رغم الحاجة للأكسجين."
                    ),
                    ErAction(
                        label = "إيقاف الصاد الثاني لتقليل الأدوية",
                        points = -5,
                        feedback = "خطأ. التغطية المزدوجة موصى بها في الشديدة ولا تُوقف قبل الاستقرار والزرع.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتضييق مبكر غير مبرر للتغطية."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في ذات الرئة الشديدة، لا تؤخر الصادات الواسعة ولا الأكسجين بانتظار الزرع؛ الشدة تحدد مكان العلاج."
    ),
    ErCase(
        id = "c_spontaneous_pneumothorax",
        title = "استرواح صدري عفوي",
        presentation = "شاب 24 سنة، نحيل وطويل، ألم صدري مفاجئ جهة اليسار مع ضيق نفس خفيف بعد سعال. لا رض. صورة الصدر: استرواح صدري أيسر كبير بلا انزياح منصف.",
        guidelineRef = "BTS Pleural Disease Guideline 2023: الاسترواح الكبير أو العرضي يُدبَّر بالشفط أو الأنبوب الصدري، والصغير غير العرضي قد يُراقب، والأكسجين يسرّع الامتصاص.",
        startNodeId = "c_spontaneous_pneumothorax_n1",
        nodes = listOf(
            ErNode(
                id = "c_spontaneous_pneumothorax_n1",
                text = "استرواح كبير مع ضيق نفس وSpO2 ‏92%. لا علامات توتر. ما التدبير؟",
                vitals = ErVitals(hr = 102, sbp = 128, dbp = 78, rr = 24, spo2 = 92, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أكسجين + شفط بالإبرة أو أنبوب صدري حسب الإمكانية والخبرة",
                        points = 10,
                        feedback = "صحيح. الاسترواح الكبير العرضي يحتاج تفريغًا فعالًا، والأكسجين يسرّع امتصاص الهواء المتبقي.",
                        nextNodeId = "c_spontaneous_pneumothorax_n2"
                    ),
                    ErAction(
                        label = "مراقبة فقط بلا تفريغ رغم كبر الحجم والأعراض",
                        points = -10,
                        feedback = "خطأ. المراقبة وحدها للصغير غير العرضي؛ الكبير العرضي قد يتوسع ويتوتر.",
                        nextNodeId = "c_spontaneous_pneumothorax_n2"
                    ),
                    ErAction(
                        label = "تهوية بضغط إيجابي قبل التفريغ",
                        points = -10,
                        feedback = "خطير. الضغط الإيجابي قبل التفريغ قد يحوّل الاسترواح إلى توتري.",
                        nextNodeId = "c_spontaneous_pneumothorax_n2"
                    ),
                    ErAction(
                        label = "إعطاء مدرّات بول لتقليل الهواء",
                        points = -5,
                        feedback = "خطأ. المدرات لا تعالج استرواحًا صدريًا.",
                        nextNodeId = "c_spontaneous_pneumothorax_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_spontaneous_pneumothorax_n2",
                text = "بعد الأنبوب الصدري تحسن الضيق وSpO2 ‏97%، لكن تسرب الهواء مستمر. ما التالي؟",
                vitals = ErVitals(hr = 88, sbp = 122, dbp = 76, rr = 18, spo2 = 97, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إبقاء الأنبوب مع شفط لطيف ومراقبة + نصيحة بالإقلاع عن التدخين ومنع الطيران والغوص",
                        points = 10,
                        feedback = "صحيح. التسرب المستمر يحتاج بقاء الأنبوب ومراقبة، والتدخين والطيران يرفعان خطر النكس.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: استرواح كبير فُرّغ بأنبوب مع مراقبة وتثقيف ضد النكس."
                    ),
                    ErAction(
                        label = "سحب الأنبوب فورًا رغم استمرار التسرب",
                        points = -10,
                        feedback = "خطأ. سحب الأنبوب مع تسرب مستمر يعيد تراكم الهواء.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بسحب مبكر للأنبوب رغم التسرب."
                    ),
                    ErAction(
                        label = "إعطاء صادات واسعة روتينيًا",
                        points = -5,
                        feedback = "غير مبرر. لا صادات روتينية لاسترواح غير مختلط بلا إنتان.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج غير مبرر."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: حجم الاسترواح والأعراض يحددان التدبير؛ الكبير العرضي لا يُترك للمراقبة وحدها."
    ),
    ErCase(
        id = "c_pleural_effusion",
        title = "انصباب جنبي كبير",
        presentation = "رجل 59 سنة، ضيق نفس متزايد منذ أسبوع، نقص أصوات التنفس في قاعدة اليسار، صورة الصدر: انصباب أيسر كبير بلا قصة قلبية واضحة.",
        guidelineRef = "BTS Pleural Disease Guideline 2023: البزل التشخيصي الموجّه بالإيكو، وتحليل السائل، والتفريغ العلاجي المتدرج مع مراقبة وذمة إعادة التوسع.",
        startNodeId = "c_pleural_effusion_n1",
        nodes = listOf(
            ErNode(
                id = "c_pleural_effusion_n1",
                text = "انصباب كبير مع ضيق نفس وSpO2 ‏91%. ما أول إجراء تشخيصي علاجي؟",
                vitals = ErVitals(hr = 108, sbp = 132, dbp = 80, rr = 26, spo2 = 91, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إيكو جنبي + بزل تشخيصي علاجي موجّه بالإيكو مع تحليل السائل",
                        points = 10,
                        feedback = "صحيح. الإيكو يحدد المكان الآمن والبزل يعطي التشخيص ويخفف الضيق.",
                        nextNodeId = "c_pleural_effusion_n2"
                    ),
                    ErAction(
                        label = "بزل أعمى سريع بكميات كبيرة بلا إيكو",
                        points = -10,
                        feedback = "خطير. البزل الأعمى يرفع خطر ثقب الرئة، والتفريغ السريع الكبير يسبب وذمة إعادة توسع.",
                        nextNodeId = "c_pleural_effusion_n2"
                    ),
                    ErAction(
                        label = "مدرّات فقط بلا بزل رغم عدم وضوح السبب",
                        points = -5,
                        feedback = "خطأ. بلا تشخيص للسائل قد تُفوَّت خباثة أو إنتان أو سل.",
                        nextNodeId = "c_pleural_effusion_n2"
                    ),
                    ErAction(
                        label = "صورة مقطعية أولًا وتأجيل البزل أيامًا",
                        points = -5,
                        feedback = "غير مثالي. المقطعي مفيد لاحقًا لكنه لا يغني عن البزل ولا يخفف الضيق الآن.",
                        nextNodeId = "c_pleural_effusion_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_pleural_effusion_n2",
                text = "السائل نضحي، وخلاياه لمفاوية. الضيق تحسن بعد تفريغ 800 مل. ما التالي؟",
                vitals = ErVitals(hr = 92, sbp = 126, dbp = 78, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إكمال الاستقصاء حسب النضح (سل، خباثة، إنتان) + متابعة وإعادة تقييم الانصباب",
                        points = 10,
                        feedback = "صحيح. النضح اللمفاوي يستدعي استقصاء سببه لا الاكتفاء بالتفريغ.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: انصباب نضحي فُرّغ بتدرج مع استقصاء سببه."
                    ),
                    ErAction(
                        label = "تفريغ 3 لترات إضافية فورًا في نفس الجلسة",
                        points = -10,
                        feedback = "خطير. التفريغ السريع الكبير يسبب وذمة إعادة توسع رئوية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتفريغ مفرط سريع."
                    ),
                    ErAction(
                        label = "إيقاف المتابعة لأن الضيق تحسن",
                        points = -5,
                        feedback = "خطأ. تحسن الأعراض لا يلغي سبب النضح.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف الاستقصاء رغم نضح لم يُفسَّر."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الانصباب الكبير يُشخَّص بالبزل الموجّه بالإيكو ويُفرَّغ بتدرج، لا بسرعة وعشوائية."
    ),
    ErCase(
        id = "c_ards",
        title = "متلازمة الضائقة التنفسية الحادة ARDS",
        presentation = "رجل 54 سنة، بعد ذات رئة شديدة، نقص أكسجة شديد رغم أكسجين عالٍ، ارتشاحات ثنائية الجانب بصورة الصدر، بلا علامات قصور قلب. نسبة PaO2/FiO2 ‏110.",
        guidelineRef = "ATS 2023 ARDS Guideline وتعاريف برلين: تهوية وقائية بحجم مدّي منخفض 6 مل/كغ وزن مثالي، ضغط هضبة أقل من 30، PEEP مناسبة، والانبطاح في الشديدة.",
        startNodeId = "c_ards_n1",
        nodes = listOf(
            ErNode(
                id = "c_ards_n1",
                text = "نقص أكسجة شديد رغم FiO2 عالٍ مع ارتشاحات ثنائية. ما استراتيجية التهوية؟",
                vitals = ErVitals(hr = 128, sbp = 118, dbp = 72, rr = 30, spo2 = 86, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "تهوية وقائية: حجم مدّي 6 مل/كغ وزن مثالي + ضغط هضبة أقل من 30 + PEEP",
                        points = 10,
                        feedback = "صحيح. الحجم المنخفض يقلل أذية الرئة المرتبطة بالتهوية ويحسن البقاء.",
                        nextNodeId = "c_ards_n2"
                    ),
                    ErAction(
                        label = "حجم مدّي كبير 12 مل/كغ لتحسين الأكسجة بسرعة",
                        points = -10,
                        feedback = "خطير. الحجوم الكبيرة تسبب رضًا ضغطيًا وحجميًا وتفاقم ARDS.",
                        nextNodeId = "c_ards_n2"
                    ),
                    ErAction(
                        label = "أكسجين فقط بلا تهوية وقائية رغم الفشل التنفسي",
                        points = -10,
                        feedback = "خطأ. الفشل التنفسي الشديد يحتاج دعمًا تهويويًا وقائيًا لا أكسجينًا وحده.",
                        nextNodeId = "c_ards_n2"
                    ),
                    ErAction(
                        label = "سوائل حرة بكميات كبيرة",
                        points = -5,
                        feedback = "خطأ. الإفراط بالسوائل يفاقم الوذمة الرئوية في ARDS؛ السوائل محافظة بعد الإنعاش.",
                        nextNodeId = "c_ards_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_ards_n2",
                text = "رغم التهوية الوقائية، PaO2/FiO2 ما زالت 110. ما الإضافة؟",
                vitals = ErVitals(hr = 120, sbp = 112, dbp = 70, rr = 28, spo2 = 88, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "وضعية الانبطاح Prone + مراجعة سبب ARDS وعلاج الإنتان المسبب",
                        points = 10,
                        feedback = "صحيح. الانبطاح يحسن الأكسجة والبقاء في ARDS الشديدة، وعلاج السبب أساسي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: ARDS شُدّدت بتهوية وقائية وانبطاح وعلاج السبب."
                    ),
                    ErAction(
                        label = "رفع الحجم المدّي مجددًا",
                        points = -10,
                        feedback = "خطأ. العودة للحجوم الكبيرة تعيد أذية الرئة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتهوية مؤذية للرئة."
                    ),
                    ErAction(
                        label = "إيقاف PEEP تمامًا",
                        points = -5,
                        feedback = "خطأ. إيقاف PEEP يسبب انخماصًا متكررًا وتدهور الأكسجة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف PEEP غير مبرر."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في ARDS، التهوية الوقائية بحجم منخفض والانبطاح في الشديدة ينقذان الرئة، لا الحجوم الكبيرة."
    ),
    ErCase(
        id = "c_foreign_body_aspiration",
        title = "استنشاق جسم أجنبي عند بالغ",
        presentation = "رجل 45 سنة، اختناق مفاجئ أثناء الأكل، سعال غير فعال، صرير، لا يستطيع الكلام، يمسك عنقه بيديه. واعٍ لكنه يزرقّ تدريجيًا.",
        guidelineRef = "Resuscitation Council UK — Choking Guideline: في الاختناق الشديد عند الواعي: ضربات ظهرية ثم دفعات بطنية، وإن فقد الوعي يُبدأ الإنعاش القلبي الرئوي، والتنظير القصبي هو الحل النهائي للجسم المتبقي.",
        startNodeId = "c_foreign_body_aspiration_n1",
        nodes = listOf(
            ErNode(
                id = "c_foreign_body_aspiration_n1",
                text = "بالغ واعٍ باختناق شديد وسعال غير فعال. ما أول فعل؟",
                vitals = ErVitals(hr = 118, sbp = 140, dbp = 85, rr = 30, spo2 = 88, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "5 ضربات ظهرية ثم 5 دفعات بطنية بالتناوب مع طلب المساعدة",
                        points = 10,
                        feedback = "صحيح. هذه هي سلسلة الاختناق عند الواعي، وتُكرر حتى خروج الجسم أو فقد الوعي.",
                        nextNodeId = "c_foreign_body_aspiration_n2"
                    ),
                    ErAction(
                        label = "إدخال الأصابع بالفم بحثًا عن الجسم أعمى",
                        points = -10,
                        feedback = "خطير. البحث الأعمى قد يدفع الجسم أعمق ويسد الطريق كليًا.",
                        nextNodeId = "c_foreign_body_aspiration_n2"
                    ),
                    ErAction(
                        label = "إعطاء ماء ليشرب ليدفع الجسم",
                        points = -10,
                        feedback = "خطير. السوائل مع طريق هوائي مسدود تزيد الشفط ولا تحل الانسداد.",
                        nextNodeId = "c_foreign_body_aspiration_n2"
                    ),
                    ErAction(
                        label = "انتظار وصول الإسعاف بلا أي مناورة",
                        points = -10,
                        feedback = "خطأ. دقائق الاختناق قاتلة؛ المناورة الفورية هي العلاج.",
                        nextNodeId = "c_foreign_body_aspiration_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_foreign_body_aspiration_n2",
                text = "خرج جزء من الطعام لكن السعال والصفير مستمران. ما التالي؟",
                vitals = ErVitals(hr = 104, sbp = 132, dbp = 82, rr = 24, spo2 = 93, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تنظير قصبي عاجل لاستخراج أي بقايا + مراقبة",
                        points = 10,
                        feedback = "صحيح. البقايا قد تسبب التهابًا أو انسدادًا متأخرًا؛ التنظير هو الاستخراج النهائي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: اختناق حُلّ بالمناورات ثم استُخرجت البقايا بالتنظير."
                    ),
                    ErAction(
                        label = "تخريج لأن جزءًا خرج والسعال خفّ قليلًا",
                        points = -10,
                        feedback = "خطأ. بقايا الجسم الأجنبي قد تسبب خراجًا أو انخماصًا متأخرًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج رغم بقايا محتملة."
                    ),
                    ErAction(
                        label = "صادات واسعة فقط بلا تنظير",
                        points = -5,
                        feedback = "ناقص. الصادات لا تستخرج جسمًا أجنبيًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج لا يزيل السبب."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الاختناق الشديد عند الواعي يُعالج بالضربات الظهرية والدفعات البطنية فورًا، لا بالبحث الأعمى بالأصابع."
    ),
    ErCase(
        id = "c_epiglottitis",
        title = "التهاب لسان المزمار الحاد عند بالغ",
        presentation = "رجل 39 سنة، ألم حلق شديد، سيلان لعاب لعدم القدرة على البلع، صوت مكتوم، صرير شهيقي، حرارة 38.8، يجلس منحنيًا للأمام. لا يستطيع الاستلقاء.",
        guidelineRef = "Difficult Airway Society 2015: في التهاب لسان المزمار، لا يُثار المريض ولا تُفحص البلعوم بالقوة؛ تأمين الطريق الهوائي بخبير تخدير وأنف وأذن مع جاهزية لخزع رغامي جراحي.",
        startNodeId = "c_epiglottitis_n1",
        nodes = listOf(
            ErNode(
                id = "c_epiglottitis_n1",
                text = "مريض بصرير وسيلان لعاب وصوت مكتوم. ما أول ما تفعله وتتجنبه؟",
                vitals = ErVitals(hr = 122, sbp = 138, dbp = 84, rr = 26, spo2 = 92, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إبقاؤه جالسًا هادئًا + أكسجين + استدعاء فوري لخبير التخدير والأنف والأذن",
                        points = 10,
                        feedback = "صحيح. الوضعية المريحة والهدوء يحميان الطريق الهوائي حتى وصول الخبير.",
                        nextNodeId = "c_epiglottitis_n2"
                    ),
                    ErAction(
                        label = "خفض لسانه بخافض لسان لرؤية لسان المزمار",
                        points = -10,
                        feedback = "خطير. الفحص القسري قد يثير تشنجًا حنجريًا وإغلاقًا كاملًا للطريق الهوائي.",
                        nextNodeId = "c_epiglottitis_n2"
                    ),
                    ErAction(
                        label = "إجباره على الاستلقاء لفحصه بشكل أفضل",
                        points = -10,
                        feedback = "خطير. الاستلقاء يفاقم انسداد الطريق الهوائي العلوي.",
                        nextNodeId = "c_epiglottitis_n2"
                    ),
                    ErAction(
                        label = "إعطاء مهدئ قوي ليرتاح",
                        points = -10,
                        feedback = "خطير. التهدئة بلا طريق هوائي مؤمّن قد تسبب توقف التنفس.",
                        nextNodeId = "c_epiglottitis_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_epiglottitis_n2",
                text = "وصل الخبير وتم تأمين الطريق الهوائي. ما العلاج التالي؟",
                vitals = ErVitals(hr = 108, sbp = 130, dbp = 80, rr = 22, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "صادات وريدية تغطي المستدمية والمكورات + مراقبة عناية + ستيرويد عند الحاجة",
                        points = 10,
                        feedback = "صحيح. بعد تأمين الطريق، الصادات هي العلاج المسبب والمراقبة بالعناية إلزامية.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: لسان مزمار أُمّن طريقه الهوائي ثم عولج بالصادات."
                    ),
                    ErAction(
                        label = "تخريج بمضاد حيوي فموي بعد تحسن بسيط",
                        points = -10,
                        feedback = "خطأ. التهاب لسان المزمار حالة عناية وقد ينتكس الانسداد.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج غير آمن."
                    ),
                    ErAction(
                        label = "إزالة الأنبوب فور تأمينه لأن المريض هدأ",
                        points = -10,
                        feedback = "خطأ. إزالة مبكرة قبل زوال الوذمة تعيد الانسداد.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإزالة مبكرة للطريق الهوائي."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في التهاب لسان المزمار، الطريق الهوائي مهدد وكل إثارة قد تغلقه؛ الخبير والهدوء قبل أي فحص قسري."
    ),
    ErCase(
        id = "c_croup",
        title = "خناق Croup عند طفل",
        presentation = "طفل عمره سنتان، سعال نباحي، صرير شهيقي، بحة، حرارة خفيفة، بدأ ليلًا بعد زكام. متيقظ، لا سيلان لعاب، ويتحسن بحضن والده.",
        guidelineRef = "Bjornson and Johnson, CMAJ 2013;185:1317-1323 وإرشادات طب الأطفال: ديكساميتازون فموي، وأدرينالين بالرذاذ للمتوسط والشديد، مع تهدئة الطفل وعدم إثارته.",
        startNodeId = "c_croup_n1",
        nodes = listOf(
            ErNode(
                id = "c_croup_n1",
                text = "طفل بصرير وسعال نباحي متوسط الشدة. ما العلاج الأول؟",
                vitals = ErVitals(hr = 128, sbp = 110, dbp = 70, rr = 30, spo2 = 94, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "ديكساميتازون فموي 0.6 ملغ/كغ + إبقاؤه هادئًا بحضن والده + أكسجين عند الحاجة",
                        points = 10,
                        feedback = "صحيح. الديكساميتازون يقلل الوذمة تحت المزمار والهدوء يمنع تفاقم الصرير.",
                        nextNodeId = "c_croup_n2"
                    ),
                    ErAction(
                        label = "صادات وريدية واسعة فورًا",
                        points = -5,
                        feedback = "غير مبرر. الخناق فيروسي عادة؛ الصادات لا تعالج الوذمة الفيروسية.",
                        nextNodeId = "c_croup_n2"
                    ),
                    ErAction(
                        label = "فحص البلعوم بالقوة وخافض اللسان",
                        points = -10,
                        feedback = "خطير. الإثارة والفحص القسري يفاقمان ضيق الطريق الهوائي العلوي.",
                        nextNodeId = "c_croup_n2"
                    ),
                    ErAction(
                        label = "تنبيب فوري",
                        points = -5,
                        feedback = "غير صحيح كخطوة أولى. التنبيب للفشل التنفسي أو عدم الاستجابة فقط.",
                        nextNodeId = "c_croup_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_croup_n2",
                text = "الصرير ازداد أثناء الراحة رغم الديكساميتازون. ما الإضافة؟",
                vitals = ErVitals(hr = 134, sbp = 112, dbp = 72, rr = 34, spo2 = 92, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أدرينالين بالرذاذ + مراقبة لصيقة لساعات لاحتمال عودة الصرير",
                        points = 10,
                        feedback = "صحيح. الأدرينالين بالرذاذ يخفف الوذمة سريعًا لكن مفعوله قصير فيحتاج مراقبة.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: خناق متوسط إلى شديد عولج بديكساميتازون وأدرينالين بالرذاذ مع مراقبة."
                    ),
                    ErAction(
                        label = "تخريج فوري بعد جرعة الأدرينالين الأولى",
                        points = -10,
                        feedback = "خطأ. مفعول الأدرينالين يزول خلال ساعتين وقد يعود الصرير.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج مبكر بعد أدرينالين بالرذاذ."
                    ),
                    ErAction(
                        label = "إعطاء مهدئ قوي",
                        points = -10,
                        feedback = "خطير. التهدئة تخفي التدهور التنفسي عند طفل بطريق هوائي ضيق.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتهدئة خطيرة."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في الخناق، الديكساميتازون أساسي والأدرينالين بالرذاذ للشديد، والهدوء جزء من العلاج."
    ),
    ErCase(
        id = "c_bronchiectasis_exacerbation",
        title = "تفاقم توسع القصبات",
        presentation = "امرأة 52 سنة، توسع قصبات معروف، زيادة حجم البلغم وتقيحه منذ 4 أيام مع ضيق نفس وحرارة 38.4. زرع سابق أظهر زائفة زنجارية.",
        guidelineRef = "BTS Guideline for Bronchiectasis 2019: في التفاقم، زرع بلغم، ومضاد حيوي يغطي الجرثومة السابقة لمدة 14 يومًا عادة، مع تصريف وتقنية تنظيف الطريق الهوائي.",
        startNodeId = "c_bronchiectasis_exacerbation_n1",
        nodes = listOf(
            ErNode(
                id = "c_bronchiectasis_exacerbation_n1",
                text = "تفاقم مع بلغم قيحي وحرارة وزرع سابق بالزائفة. ما العلاج؟",
                vitals = ErVitals(hr = 110, sbp = 128, dbp = 78, rr = 24, spo2 = 92, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "زرع بلغم جديد + مضاد حيوي يغطي الزائفة + تقنية تنظيف الطريق الهوائي",
                        points = 10,
                        feedback = "صحيح. التغطية الموجهة بالزرع السابق مع تصريف الإفرازات هي أساس العلاج.",
                        nextNodeId = "c_bronchiectasis_exacerbation_n2"
                    ),
                    ErAction(
                        label = "مضاد حيوي ضيق الطيف لا يغطي الزائفة",
                        points = -5,
                        feedback = "غير كافٍ. الزائفة السابقة تستدعي تغطية فعالة ضدها.",
                        nextNodeId = "c_bronchiectasis_exacerbation_n2"
                    ),
                    ErAction(
                        label = "ستيرويد جهازي وحده بلا مضاد حيوي",
                        points = -5,
                        feedback = "خطأ. التفاقم القيحي إنتاني بالأساس ويحتاج مضادًا حيويًا.",
                        nextNodeId = "c_bronchiectasis_exacerbation_n2"
                    ),
                    ErAction(
                        label = "موسع قصبي فقط",
                        points = -5,
                        feedback = "ناقص. الموسع مساعد لكنه لا يعالج الإنتان القيحي.",
                        nextNodeId = "c_bronchiectasis_exacerbation_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_bronchiectasis_exacerbation_n2",
                text = "تحسنت الحرارة لكن البلغم ما زال غزيرًا. ما التالي؟",
                vitals = ErVitals(hr = 96, sbp = 124, dbp = 76, rr = 20, spo2 = 95, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إكمال 14 يومًا من المضاد الحيوي + علاج فيزيائي تنفسي ومتابعة",
                        points = 10,
                        feedback = "صحيح. مدة العلاج الكافية والتصريف يمنعان النكس المبكر.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تفاقم توسع قصبات عولج بمضاد موجّه وتصريف ومدة كافية."
                    ),
                    ErAction(
                        label = "إيقاف المضاد الحيوي بعد 3 أيام لأن الحرارة زالت",
                        points = -5,
                        feedback = "خطأ. الإيقاف المبكر في توسع القصبات يسبب نكسًا ومقاومة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للمضاد الحيوي."
                    ),
                    ErAction(
                        label = "إضافة ستيرويد طويل بلا استطباب",
                        points = -5,
                        feedback = "غير مبرر. الستيرويد الجهازي المطوّل ليس علاج التفاقم القيحي.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج غير مبرر."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تفاقم توسع القصبات، المضاد الحيوي يُوجَّه بالزرع السابق ويُعطى مدة كافية مع تنظيف الطريق الهوائي."
    ),
    ErCase(
        id = "c_ipf_exacerbation",
        title = "تفاقم حاد للتليف الرئوي مجهول السبب",
        presentation = "رجل 66 سنة، تليف رئوي معروف، ضيق نفس متفاقم خلال أسبوعين مع نقص أكسجة شديد وارتشاحات جديدة ثنائية، بلا قصور قلب واضح.",
        guidelineRef = "ATS/ERS/JRS/ALAT 2022 لتليف الرئة مجهول السبب وتفاقمه الحاد: استبعاد الإنتان والانصمام، وستيرويد عالي الجرعة، ودعم بالأكسجين، وتقييم زرع الرئة.",
        startNodeId = "c_ipf_exacerbation_n1",
        nodes = listOf(
            ErNode(
                id = "c_ipf_exacerbation_n1",
                text = "تليف معروف مع تدهور حاد وارتشاحات جديدة. ما أول استقصاء؟",
                vitals = ErVitals(hr = 122, sbp = 130, dbp = 80, rr = 30, spo2 = 87, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أكسجين + استبعاد الإنتان والانصمام الرئوي وقصور القلب بالزرع والتحاليل والتصوير",
                        points = 10,
                        feedback = "صحيح. التفاقم تشخيص استبعادي؛ الإنتان والانصمام والقلب يجب نفيها أولًا.",
                        nextNodeId = "c_ipf_exacerbation_n2"
                    ),
                    ErAction(
                        label = "إعطاء مدرّات فقط باعتبارها وذمة قلبية",
                        points = -5,
                        feedback = "خطأ. بلا دليل قلبي، المدرات لا تعالج التفاقم وقد تضر.",
                        nextNodeId = "c_ipf_exacerbation_n2"
                    ),
                    ErAction(
                        label = "تنبيب فوري بلا استقصاء",
                        points = -5,
                        feedback = "غير صحيح كخطوة أولى. التهوية الباضعة في التليف المتقدم إنذارها سيئ وتحتاج قرارًا مدروسًا.",
                        nextNodeId = "c_ipf_exacerbation_n2"
                    ),
                    ErAction(
                        label = "صادات واسعة فقط واعتبار الأمر ذات رئة حتمًا",
                        points = -5,
                        feedback = "ناقص. الإنتان احتمال واحد فقط؛ التفاقم نفسه يحتاج تدبيرًا خاصًا.",
                        nextNodeId = "c_ipf_exacerbation_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_ipf_exacerbation_n2",
                text = "استُبعد الإنتان والانصمام والقلب. ما العلاج؟",
                vitals = ErVitals(hr = 118, sbp = 128, dbp = 78, rr = 28, spo2 = 89, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "ستيرويد عالي الجرعة + أكسجين ودعم + إحالة لتقييم زرع الرئة",
                        points = 10,
                        feedback = "صحيح. هذا هو تدبير التفاقم الحاد بعد الاستبعاد، مع بحث خيار الزرع.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تفاقم تليف حاد استُبعدت أسبابه ثم عولج بالستيرويد والدعم."
                    ),
                    ErAction(
                        label = "علاج مثبط مناعي ثلاثي روتيني",
                        points = -10,
                        feedback = "خطأ. التثبيط الثلاثي زاد الوفيات في التليف ولا يُستخدم للتفاقم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتثبيط مناعي ضار."
                    ),
                    ErAction(
                        label = "تخريج بأكسجين منزلي فقط",
                        points = -5,
                        feedback = "ناقص. التفاقم الحاد حالة عناية وتحتاج ستيرويدًا ومراقبة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج غير آمن."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: التفاقم الحاد للتليف يُشخَّص باستبعاد الأسباب الأخرى ويُعالج بالستيرويد والدعم، مع تقييم زرع الرئة."
    ),
    ErCase(
        id = "c_hereditary_angioedema",
        title = "وذمة وعائية وراثية حادة",
        presentation = "امرأة 29 سنة، وذمة وعائية وراثية معروفة، تورم شفاه ولسان متزايد منذ ساعة بلا شرى ولا حكة، بعد رض خفيف. صوتها بدأ يتغير.",
        guidelineRef = "WAO Guideline for Hereditary Angioedema 2021: العلاج النوعي هو مركز مثبط C1 أو إيكاتيبانت؛ مضاد الهستامين والستيرويد والأدرينالين غير فعالة في الوذمة المعتمدة على البراديكينين.",
        startNodeId = "c_hereditary_angioedema_n1",
        nodes = listOf(
            ErNode(
                id = "c_hereditary_angioedema_n1",
                text = "تورم لسان متزايد بلا شرى عند مريضة بوذمة وراثية. ما العلاج؟",
                vitals = ErVitals(hr = 110, sbp = 136, dbp = 84, rr = 22, spo2 = 94, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "مركز مثبط C1 وريدي أو إيكاتيبانت فورًا + مراقبة الطريق الهوائي بخبير",
                        points = 10,
                        feedback = "صحيح. العلاج النوعي يوقف البراديكينين، ومراقبة الطريق الهوائي إلزامية.",
                        nextNodeId = "c_hereditary_angioedema_n2"
                    ),
                    ErAction(
                        label = "مضاد هستامين وستيرويد فقط وانتظار التحسن",
                        points = -10,
                        feedback = "خطأ. هذه الأدوية لا توقف الوذمة المعتمدة على البراديكينين وتأخير العلاج النوعي خطير.",
                        nextNodeId = "c_hereditary_angioedema_n2"
                    ),
                    ErAction(
                        label = "أدرينالين عضلي كعلاج وحيد",
                        points = -5,
                        feedback = "غير كافٍ. الأدرينالين لا يعالج الوذمة الوراثية وإن كان مساعدًا عند الشك بالتأق.",
                        nextNodeId = "c_hereditary_angioedema_n2"
                    ),
                    ErAction(
                        label = "إعطاء مدرّات لتقليل التورم",
                        points = -5,
                        feedback = "خطأ. المدرات لا تعالج وذمة وعائية.",
                        nextNodeId = "c_hereditary_angioedema_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_hereditary_angioedema_n2",
                text = "التورم بدأ يخف لكن الطريق الهوائي ما زال مهددًا. ما التالي؟",
                vitals = ErVitals(hr = 100, sbp = 130, dbp = 80, rr = 20, spo2 = 96, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "مراقبة لصيقة بالعناية + جاهزية لتأمين الطريق الهوائي + تثقيف بخطة طوارئ وعلاج منزلي",
                        points = 10,
                        feedback = "صحيح. الوذمة قد تتقدم رغم العلاج الأولي؛ المراقبة وخطة الطوارئ تحميان المريضة.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: وذمة وراثية عولجت نوعيًا مع مراقبة طريق هوائي وخطة طوارئ."
                    ),
                    ErAction(
                        label = "تخريج فوري لأن التورم خفّ قليلًا",
                        points = -10,
                        feedback = "خطأ. الوذمة قد تعود وتغلق الطريق الهوائي بعد التحسن الأولي.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج مبكر رغم تهديد الطريق الهوائي."
                    ),
                    ErAction(
                        label = "إعطاء مضاد هستامين طويل الأمد كوقاية وحيدة",
                        points = -5,
                        feedback = "خطأ. الوقاية في الوذمة الوراثية نوعية وليست بمضاد الهستامين.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بوقاية غير فعالة."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الوذمة الوراثية بلا شرى لا تستجيب لمضاد الهستامين؛ علاجها النوعي مركز C1 أو إيكاتيبانت مع حماية الطريق الهوائي."
    ),
    ErCase(
        id = "c_massive_hemoptysis",
        title = "نفث دموي غزير",
        presentation = "رجل 48 سنة، سل رئوي قديم، نفث دموي غزير مفاجئ أكثر من 300 مل خلال ساعات، ضيق نفس، SpO2 ‏90%، ضغط 110/70.",
        guidelineRef = "ACR Appropriateness Criteria: Hemoptysis (revised 2019): حماية الطريق الهوائي مع إبقاء الجانب النازف للأسفل، وتصوير وتدبير السبب، والإصمام الشرياني القصبي هو العلاج النهائي في كثير من الحالات.",
        startNodeId = "c_massive_hemoptysis_n1",
        nodes = listOf(
            ErNode(
                id = "c_massive_hemoptysis_n1",
                text = "نفث غزير مع نقص أكسجة. ما أول إجراء؟",
                vitals = ErVitals(hr = 118, sbp = 110, dbp = 70, rr = 28, spo2 = 90, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "حماية الطريق الهوائي + وضعية الجانب النازف للأسفل + أكسجين + وريدان واسعان وطلب دم",
                        points = 10,
                        feedback = "صحيح. حماية الرئة السليمة من الدم ومنع الشفط تسبق أي استقصاء.",
                        nextNodeId = "c_massive_hemoptysis_n2"
                    ),
                    ErAction(
                        label = "إعطاء مهدئ قوي وسعال مثبط فقط",
                        points = -10,
                        feedback = "خطير. التهدئة تخفي النزف وتضعف حماية الطريق الهوائي.",
                        nextNodeId = "c_massive_hemoptysis_n2"
                    ),
                    ErAction(
                        label = "إعطاء مضاد تخثر لاشتباه انصمام",
                        points = -10,
                        feedback = "خطير. مضاد التخثر في نفث غزير يفاقم النزف وقد يقتل.",
                        nextNodeId = "c_massive_hemoptysis_n2"
                    ),
                    ErAction(
                        label = "صورة صدر فقط وانتظار توقف النزف تلقائيًا",
                        points = -5,
                        feedback = "خطأ. النفث الغزير لا يُترك للانتظار بلا حماية طريق هوائي.",
                        nextNodeId = "c_massive_hemoptysis_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_massive_hemoptysis_n2",
                text = "تم تأمين الطريق الهوائي والنزف مستمر باعتدال. ما العلاج النهائي؟",
                vitals = ErVitals(hr = 108, sbp = 106, dbp = 68, rr = 24, spo2 = 93, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تنظير قصبي لتحديد المصدر + إصمام شرياني قصبي عاجل",
                        points = 10,
                        feedback = "صحيح. الإصمام هو العلاج النهائي الأكثر شيوعًا للنفث الغزير غير الجراحي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: نفث غزير حُمي طريقه الهوائي ثم أُوقف بالإصمام."
                    ),
                    ErAction(
                        label = "إعطاء صادات واسعة فقط",
                        points = -5,
                        feedback = "ناقص. الصادات لا توقف نزفًا شريانيًا قصبيًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج لا يوقف النزف."
                    ),
                    ErAction(
                        label = "تخريج بعد توقف مؤقت للنزف",
                        points = -10,
                        feedback = "خطأ. النفث الغزير ينكس غالبًا بلا علاج نهائي.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج رغم خطر النكس."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في النفث الغزير، حماية الطريق الهوائي أولًا ثم إيقاف النزف بالإصمام أو الجراحة، لا الانتظار والمهدئات."
    ),
    ErCase(
        id = "c_dka",
        title = "حماض كيتوني سكري DKA",
        presentation = "شاب 21 سنة، سكري نمط أول، غثيان وقيء وألم بطني منذ يوم، تنفس عميق سريع، رائحة أسيتون، جفاف شديد. سكر 520 ملغ/دل، pH ‏7.12، بيكربونات 9، كيتونات قوية.",
        guidelineRef = "ADA Standards of Care 2025: سوائل أولًا، ثم أنسولين وريدي 0.1 وحدة/كغ/ساعة بعد تصحيح البوتاسيوم، وإضافة دكستروز عند سكر أقل من 200-250، وبيكربونات فقط عند pH أقل من 6.9.",
        startNodeId = "c_dka_n1",
        nodes = listOf(
            ErNode(
                id = "c_dka_n1",
                text = "DKA شديد مع جفاف وpH ‏7.12. ما أول خطوة؟",
                vitals = ErVitals(hr = 118, sbp = 98, dbp = 62, rr = 28, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "سوائل ملحية وريدية + قياس بوتاسيوم وغازات وكيتونات ومراقبة",
                        points = 10,
                        feedback = "صحيح. الإنعاش بالسوائل أولًا وقياس البوتاسيوم يحدد أمان بدء الأنسولين.",
                        nextNodeId = "c_dka_n2"
                    ),
                    ErAction(
                        label = "أنسولين وريدي جرعة كبيرة فورًا قبل السوائل والبوتاسيوم",
                        points = -10,
                        feedback = "خطير. الأنسولين قبل تصحيح البوتاسيوم قد يسبب نقص بوتاسيوم قاتلًا واضطراب نظم.",
                        nextNodeId = "c_dka_n2"
                    ),
                    ErAction(
                        label = "بيكربونات وريدية فورًا لأن pH منخفض",
                        points = -5,
                        feedback = "غير صحيح. البيكربونات لا تُعطى روتينيًا في DKA إلا عند pH أقل من 6.9.",
                        nextNodeId = "c_dka_n2"
                    ),
                    ErAction(
                        label = "أنسولين تحت الجلد فقط وتخريج",
                        points = -10,
                        feedback = "خطير. DKA الشديد يحتاج علاجًا وريديًا ومراقبة بالعناية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتدبير غير آمن لـDKA شديد."
                    ),
                )
            ),
            ErNode(
                id = "c_dka_n2",
                text = "البوتاسيوم 4.2 بعد السوائل الأولى. ما التالي؟",
                vitals = ErVitals(hr = 108, sbp = 108, dbp = 66, rr = 24, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "أنسولين وريدي 0.1 وحدة/كغ/ساعة + تعويض بوتاسيوم بالسوائل ومراقبة سكر وكهارل كل ساعة إلى ساعتين",
                        points = 10,
                        feedback = "صحيح. هذه هي جرعة الأنسولين القياسية مع مراقبة البوتاسيوم المستمرة.",
                        nextNodeId = "c_dka_n3"
                    ),
                    ErAction(
                        label = "إيقاف السوائل والاكتفاء بالأنسولين",
                        points = -5,
                        feedback = "خطأ. السوائل مستمرة لتصحيح الجفاف ونقص الحجم.",
                        nextNodeId = "c_dka_n3"
                    ),
                    ErAction(
                        label = "إعطاء بيكربونات لأن البيكربونات ما زالت منخفضة",
                        points = -5,
                        feedback = "خطأ. البيكربونات تتحسن مع الأنسولين والسوائل ولا تُعطى روتينيًا.",
                        nextNodeId = "c_dka_n3"
                    ),
                    ErAction(
                        label = "جرعة أنسولين مضاعفة لتسريع إغلاق الفجوة الأنيونية",
                        points = -10,
                        feedback = "خطير. الجرعات العالية تسبب نقص سكر ونقص بوتاسيوم ووذمة دماغية.",
                        nextNodeId = "c_dka_n3"
                    ),
                )
            ),
            ErNode(
                id = "c_dka_n3",
                text = "السكر الآن 230 والفجوة الأنيونية ما زالت مفتوحة. ما التالي؟",
                vitals = ErVitals(hr = 96, sbp = 116, dbp = 72, rr = 20, spo2 = 99, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إضافة دكستروز للسوائل مع استمرار الأنسولين حتى إغلاق الفجوة",
                        points = 10,
                        feedback = "صحيح. الدكستروز يسمح باستمرار الأنسولين لإغلاق الكيتونات بلا نقص سكر.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: DKA عولج بالسوائل والأنسولين والدكستروز حتى إغلاق الفجوة."
                    ),
                    ErAction(
                        label = "إيقاف الأنسولين لأن السكر انخفض إلى 230",
                        points = -10,
                        feedback = "خطأ. إيقاف الأنسولين قبل إغلاق الفجوة يعيد الكيتونات.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للأنسولين قبل إغلاق الفجوة."
                    ),
                    ErAction(
                        label = "إعطاء سكر فموي فقط",
                        points = -5,
                        feedback = "غير كافٍ. المريض ما زال بحماض ويحتاج أنسولينًا وريديًا ودكستروزًا وريديًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتدبير ناقص."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في DKA، السوائل ثم الأنسولين بعد تأمين البوتاسيوم، ولا بيكربونات روتينية ولا أنسولين قبل السوائل والبوتاسيوم."
    ),
    ErCase(
        id = "c_hhs",
        title = "حالة فرط الأسمولية السكري HHS",
        presentation = "رجل 74 سنة، سكري نمط ثانٍ، عطش وتبول كثير منذ أيام ثم ارتباك وتجفاف شديد. سكر 980 ملغ/دل، أسمولية 340، pH ‏7.32، كيتونات خفيفة فقط.",
        guidelineRef = "ADA Standards of Care 2025: في HHS، السوائل أولًا وبكميات كبيرة، ثم أنسولين بعد بدء السوائل، وتصحيح الأسمولية ببطء، ومراقبة الصوديوم والبوتاسيوم.",
        startNodeId = "c_hhs_n1",
        nodes = listOf(
            ErNode(
                id = "c_hhs_n1",
                text = "سكر 980 وأسمولية 340 مع تجفاف شديد وارتباك. ما أول خطوة؟",
                vitals = ErVitals(hr = 112, sbp = 96, dbp = 60, rr = 22, spo2 = 96, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "سوائل ملحية وريدية بكميات كبيرة + مراقبة أسمولية وكهارل",
                        points = 10,
                        feedback = "صحيح. الإنعاش بالسوائل هو العلاج الأول في HHS قبل الأنسولين.",
                        nextNodeId = "c_hhs_n2"
                    ),
                    ErAction(
                        label = "أنسولين جرعة كبيرة فورًا لخفض السكر بسرعة",
                        points = -10,
                        feedback = "خطير. خفض السكر السريع قبل السوائل يسبب انخفاضًا مفاجئًا بالأسمولية ووذمة دماغية.",
                        nextNodeId = "c_hhs_n2"
                    ),
                    ErAction(
                        label = "سوائل سكرية فورًا",
                        points = -10,
                        feedback = "خطأ. المريض بسكر 980؛ السوائل السكرية تفاقم فرط الأسمولية.",
                        nextNodeId = "c_hhs_n2"
                    ),
                    ErAction(
                        label = "مدرّات بول لتقليل الأسمولية",
                        points = -10,
                        feedback = "خطير. المدرات تفاقم الجفاف ونقص الحجم.",
                        nextNodeId = "c_hhs_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_hhs_n2",
                text = "بعد السوائل الأولى، السكر 760 والبوتاسيوم 4.0. ما التالي؟",
                vitals = ErVitals(hr = 104, sbp = 106, dbp = 64, rr = 20, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "أنسولين وريدي منخفض الجرعة + استمرار السوائل ومراقبة الأسمولية والتصحيح البطيء",
                        points = 10,
                        feedback = "صحيح. الأنسولين بعد السوائل مع تصحيح بطيء للأسمولية هو الآمن.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: HHS عولجت بالسوائل أولًا ثم أنسولين وتصحيح بطيء."
                    ),
                    ErAction(
                        label = "خفض السكر إلى الطبيعي خلال ساعات قليلة",
                        points = -10,
                        feedback = "خطأ. التصحيح السريع للأسمولية يسبب وذمة دماغية خاصة عند المسنين.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتصحيح سريع خطير للأسمولية."
                    ),
                    ErAction(
                        label = "إيقاف السوائل والاكتفاء بالأنسولين",
                        points = -5,
                        feedback = "خطأ. الجفاف في HHS هائل والسوائل مستمرة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للسوائل."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في HHS، الجفاف والأسمولية هما الخطر الأكبر؛ السوائل أولًا والتصحيح البطيء يمنعان الوذمة الدماغية."
    ),
    ErCase(
        id = "c_hyponatremia_severe",
        title = "نقص صوديوم حاد شديد مع أعراض عصبية",
        presentation = "امرأة 62 سنة، بدأت مدرّات ثيازيدية حديثًا، غثيان وارتباك ونوبة تشنج واحدة. صوديوم 112 ملي مول/لتر، بدأ الانخفاض خلال يومين.",
        guidelineRef = "European Clinical Practice Guideline 2014 لنقص الصوديوم: في الأعراض الشديدة، محلول ملحي مفرط التوتر 3% بجرعات صغيرة، وحد التصحيح 10 ملي مول/لتر في 24 ساعة لتجنب متلازمة إزالة الميالين.",
        startNodeId = "c_hyponatremia_severe_n1",
        nodes = listOf(
            ErNode(
                id = "c_hyponatremia_severe_n1",
                text = "صوديوم 112 مع تشنج وارتباك. ما أول علاج؟",
                vitals = ErVitals(hr = 104, sbp = 132, dbp = 82, rr = 20, spo2 = 97, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "محلول ملحي مفرط التوتر 3% بجرعة 150 مل وريدي + مراقبة صوديوم متكررة",
                        points = 10,
                        feedback = "صحيح. الجرعة الصغيرة المفرطة التوتر ترفع الصوديوم بسرعة كافية لوقف التشنج.",
                        nextNodeId = "c_hyponatremia_severe_n2"
                    ),
                    ErAction(
                        label = "سوائل ملحية طبيعية بكميات كبيرة",
                        points = -5,
                        feedback = "غير كافٍ. المحلول الطبيعي قد لا يرفع الصوديوم بسرعة كافية في الأعراض الشديدة.",
                        nextNodeId = "c_hyponatremia_severe_n2"
                    ),
                    ErAction(
                        label = "سوائل سكرية حرة بكميات كبيرة",
                        points = -10,
                        feedback = "خطير. السوائل الحرة تفاقم نقص الصوديوم.",
                        nextNodeId = "c_hyponatremia_severe_n2"
                    ),
                    ErAction(
                        label = "مدرّات إضافية",
                        points = -10,
                        feedback = "خطير. المدرات هي سبب محتمل وتفاقم النقص.",
                        nextNodeId = "c_hyponatremia_severe_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_hyponatremia_severe_n2",
                text = "توقف التشنج والصوديوم الآن 118 بعد 6 ساعات. ما قاعدة التصحيح التالية؟",
                vitals = ErVitals(hr = 96, sbp = 128, dbp = 80, rr = 18, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "إبطاء التصحيح ليبقى الارتفاع الكلي أقل من 10 ملي مول/لتر في 24 ساعة + إيقاف الثيازيد",
                        points = 10,
                        feedback = "صحيح. تجاوز الحد يسبب متلازمة إزالة الميالين التناضحية.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: نقص صوديوم عرضي عولج بمفرط التوتر ثم تصحيح مضبوط."
                    ),
                    ErAction(
                        label = "رفع الصوديوم إلى الطبيعي خلال 12 ساعة",
                        points = -10,
                        feedback = "خطير. التصحيح السريع المفرط يسبب إزالة ميالين وشللًا دائمًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتصحيح سريع مفرط."
                    ),
                    ErAction(
                        label = "إعادة الثيازيد فورًا",
                        points = -10,
                        feedback = "خطأ. الثيازيد سبب محتمل ولا يُعاد قبل مراجعة السبب.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعادة الدواء المسبب."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: نقص الصوديوم العرضي الشديد يُعالج بمحلول مفرط التوتر فورًا، لكن التصحيح السريع المفرط يسبب أذية دماغية دائمة."
    ),
    ErCase(
        id = "c_hypernatremia",
        title = "فرط صوديوم شديد مع تجفاف",
        presentation = "رجل 79 سنة، يعيش وحده، ارتباك وعطش شديد وقلة شرب منذ أيام، بول مركز قليل. صوديوم 162 ملي مول/لتر، يوريا مرتفعة.",
        guidelineRef = "Adrogué and Madias, N Engl J Med 2000, Hypernatremia: سوائل ناقصة التوتر، وتصحيح لا يتجاوز 10-12 ملي مول/لتر يوميًا، وتحديد السبب ومنها السكري الكاذب.",
        startNodeId = "c_hypernatremia_n1",
        nodes = listOf(
            ErNode(
                id = "c_hypernatremia_n1",
                text = "صوديوم 162 مع تجفاف وارتباك. ما أول تدبير؟",
                vitals = ErVitals(hr = 108, sbp = 104, dbp = 64, rr = 22, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "سوائل وريدية ناقصة التوتر بحذر + حساب عجز الماء ومراقبة صوديوم متكررة",
                        points = 10,
                        feedback = "صحيح. التعويض البطيء بالماء الحر هو العلاج مع مراقبة لصيقة.",
                        nextNodeId = "c_hypernatremia_n2"
                    ),
                    ErAction(
                        label = "محلول ملحي مفرط التوتر",
                        points = -10,
                        feedback = "خطأ قاتل. المفرط التوتر يرفع الصوديوم أكثر.",
                        nextNodeId = "c_hypernatremia_n2"
                    ),
                    ErAction(
                        label = "تصحيح الصوديوم إلى الطبيعي خلال 6 ساعات",
                        points = -10,
                        feedback = "خطير. التصحيح السريع يسبب وذمة دماغية.",
                        nextNodeId = "c_hypernatremia_n2"
                    ),
                    ErAction(
                        label = "مدرّات بول لتقليل الصوديوم",
                        points = -5,
                        feedback = "خطأ. المدرات تفاقم نقص الماء.",
                        nextNodeId = "c_hypernatremia_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_hypernatremia_n2",
                text = "الصوديوم ينخفض ببطء والبول ما زال مركزًا جدًا. ما الاستقصاء التالي؟",
                vitals = ErVitals(hr = 100, sbp = 112, dbp = 70, rr = 20, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "تقييم السكري الكاذب (أسمولية بول ودم، استجابة للديسموبريسين) ومراجعة الأدوية والوصول للماء",
                        points = 10,
                        feedback = "صحيح. البول المركز رغم فرط الصوديوم يستدعي نفي السكري الكاذب وأسباب قلة الشرب.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: فرط صوديوم صُحّح ببطء مع تحديد سببه."
                    ),
                    ErAction(
                        label = "إيقاف كل السوائل لأن الصوديوم بدأ ينخفض",
                        points = -5,
                        feedback = "خطأ. إيقاف السوائل يعيد الارتفاع.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للسوائل."
                    ),
                    ErAction(
                        label = "إعطاء ملح فموي لتعويض الضعف",
                        points = -10,
                        feedback = "خطأ. الملح يفاقم فرط الصوديوم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعطاء ملح في فرط صوديوم."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: فرط الصوديوم يُصحَّح ببطء؛ التصحيح السريع يسبب وذمة دماغية."
    ),
    ErCase(
        id = "c_hypercalcemia",
        title = "فرط كالسيوم شديد",
        presentation = "امرأة 64 سنة، خباثة معروفة، تعب وآلام عظمية وإمساك وارتباك، بول كثير. كالسيوم مصحح 14.5 ملغ/دل، كرياتينين مرتفع قليلًا.",
        guidelineRef = "Endocrine Society Guideline لفرط الكالسيوم: إماهة ملحية وريدية أولًا، ثم بيسفوسفونات، والكالسيتونين كجسر سريع، مع تجنب الثيازيد وعلاج السبب.",
        startNodeId = "c_hypercalcemia_n1",
        nodes = listOf(
            ErNode(
                id = "c_hypercalcemia_n1",
                text = "كالسيوم 14.5 مع ارتباك وتجفاف. ما أول علاج؟",
                vitals = ErVitals(hr = 102, sbp = 118, dbp = 74, rr = 20, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "سوائل ملحية وريدية بكميات كبيرة + مراقبة كالسيوم وكلى وبول",
                        points = 10,
                        feedback = "صحيح. الإماهة تزيد طرح الكالسيوم بالبول وتصحح الجفاف.",
                        nextNodeId = "c_hypercalcemia_n2"
                    ),
                    ErAction(
                        label = "مدرّات عروية فورًا قبل الإماهة",
                        points = -10,
                        feedback = "خطأ. المدرات قبل الإماهة تفاقم الجفاف والقصور الكلوي.",
                        nextNodeId = "c_hypercalcemia_n2"
                    ),
                    ErAction(
                        label = "ثيازيد لخفض ضغطها",
                        points = -10,
                        feedback = "خطير. الثيازيد يرفع الكالسيوم.",
                        nextNodeId = "c_hypercalcemia_n2"
                    ),
                    ErAction(
                        label = "كالسيوم فموي لتعويض آلام العظام",
                        points = -10,
                        feedback = "خطأ قاتل. إضافة كالسيوم في فرط كالسيوم شديد.",
                        nextNodeId = "c_hypercalcemia_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_hypercalcemia_n2",
                text = "بعد الإماهة، الكالسيوم ما زال 13.2. ما التالي؟",
                vitals = ErVitals(hr = 96, sbp = 122, dbp = 76, rr = 18, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "زوليدرونات وريدي + كالسيتونين كجسر + علاج السبب الخبيث",
                        points = 10,
                        feedback = "صحيح. البيسفوسفونات هي العلاج النهائي والكالسيتونين يخفض سريعًا لأيام قليلة.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: فرط كالسيوم عولج بالإماهة ثم بيسفوسفونات وكالسيتونين."
                    ),
                    ErAction(
                        label = "الاكتفاء بالإماهة وحدها رغم استمرار الارتفاع",
                        points = -5,
                        feedback = "ناقص. الإماهة وحدها لا تكفي في فرط الكالسيوم الخبيث الشديد.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإماهة وحيدة غير كافية."
                    ),
                    ErAction(
                        label = "إعطاء فيتامين د بجرعات عالية",
                        points = -10,
                        feedback = "خطأ. فيتامين د يرفع امتصاص الكالسيوم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج يرفع الكالسيوم."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في فرط الكالسيوم الشديد، الإماهة أولًا ثم البيسفوسفونات؛ الكالسيتونين جسر سريع لا علاج وحيد."
    ),
    ErCase(
        id = "c_hypocalcemia_tetany",
        title = "نقص كالسيوم مع كزاز",
        presentation = "امرأة 41 سنة، بعد استئصال درقية، تنميل حول الفم وتشنجات عضلية وتكزز، علامة تروسو إيجابية. كالسيوم 6.8 ملغ/دل، فوسفات مرتفع، PTH منخفض.",
        guidelineRef = "Brandi et al., J Clin Endocrinol Metab 2016;101:2273-2283, doi 10.1210/jc.2015-3907 لقصور جارات الدرقية: في الكزاز العرضي، كالسيوم وريدي بطيء مع مراقبة تخطيطية، وتصحيح المغنيسيوم، ثم كالسيوم فموي وفيتامين د.",
        startNodeId = "c_hypocalcemia_tetany_n1",
        nodes = listOf(
            ErNode(
                id = "c_hypocalcemia_tetany_n1",
                text = "كزاز مع كالسيوم 6.8 وQT طويل. ما أول علاج؟",
                vitals = ErVitals(hr = 108, sbp = 124, dbp = 78, rr = 20, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "غلوكونات الكالسيوم وريدي ببطء مع مراقبة تخطيطية",
                        points = 10,
                        feedback = "صحيح. الكالسيوم الوريدي البطيء يوقف الكزاز بأمان مع مراقبة القلب.",
                        nextNodeId = "c_hypocalcemia_tetany_n2"
                    ),
                    ErAction(
                        label = "كلوريد الكالسيوم بجرعة سريعة بلا مراقبة",
                        points = -5,
                        feedback = "غير آمن. الحقن السريع يسبب اضطراب نظم؛ الغلوكونات البطيء مع المراقبة هو الآمن.",
                        nextNodeId = "c_hypocalcemia_tetany_n2"
                    ),
                    ErAction(
                        label = "مهدئ فقط لتهدئة التشنجات",
                        points = -10,
                        feedback = "خطأ. التهدئة تخفي الكزاز ولا تصحح الكالسيوم.",
                        nextNodeId = "c_hypocalcemia_tetany_n2"
                    ),
                    ErAction(
                        label = "مدرّات عروية",
                        points = -10,
                        feedback = "خطأ. المدرات العروية تزيد طرح الكالسيوم.",
                        nextNodeId = "c_hypocalcemia_tetany_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_hypocalcemia_tetany_n2",
                text = "تحسن الكزاز لكن الكالسيوم ما زال منخفضًا والمغنيسيوم منخفض. ما التالي؟",
                vitals = ErVitals(hr = 96, sbp = 122, dbp = 76, rr = 18, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تصحيح المغنيسيوم + كالسيوم فموي وكالسيتريول ومتابعة PTH",
                        points = 10,
                        feedback = "صحيح. نقص المغنيسيوم يمنع إفراز PTH وتصحيح الكالسيوم؛ العلاج الفموي يثبت المستوى.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: كزاز عولج بكالسيوم وريدي ثم تصحيح مغنيسيوم وعلاج فموي."
                    ),
                    ErAction(
                        label = "إيقاف كل العلاج لأن الكزاز زال",
                        points = -5,
                        feedback = "خطأ. الكالسيوم سينخفض مجددًا بلا علاج صيانة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للعلاج."
                    ),
                    ErAction(
                        label = "إعطاء بيسفوسفونات",
                        points = -10,
                        feedback = "خطأ. البيسفوسفونات تخفض الكالسيوم أكثر.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج يفاقم النقص."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الكزاز بنقص الكالسيوم يُعالج بكالسيوم وريدي بطيء مع مراقبة القلب، وتصحيح المغنيسيوم ضروري لنجاح العلاج."
    ),
    ErCase(
        id = "c_aki",
        title = "إصابة كلوية حادة",
        presentation = "رجل 72 سنة، إسهال وقيء منذ يومين مع قلة بول، يأخذ مثبط ACE ومدرّ ومضاد التهاب غير ستيرويدي. كرياتينين ارتفع من 1.1 إلى 3.4، بوتاسيوم 5.8.",
        guidelineRef = "KDIGO 2012 Clinical Practice Guideline for AKI: إيقاف الأدوية الكلوية السمية، وإنعاش بالسوائل حسب الحجم، ومراقبة الكهارل، وغسيل عند الاستطبابات الطارئة.",
        startNodeId = "c_aki_n1",
        nodes = listOf(
            ErNode(
                id = "c_aki_n1",
                text = "إصابة كلوية حادة مع نقص حجم وأدوية كلوية سمية. ما أول خطوة؟",
                vitals = ErVitals(hr = 108, sbp = 98, dbp = 62, rr = 20, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "إيقاف مثبط ACE والمدر ومضاد الالتهاب + سوائل وريدية مدروسة + مراقبة بول وكهارل",
                        points = 10,
                        feedback = "صحيح. إزالة المسبب وإنعاش الحجم هما أساس علاج الإصابة قبل الكلوية.",
                        nextNodeId = "c_aki_n2"
                    ),
                    ErAction(
                        label = "الاستمرار بالأدوية الثلاثة وإضافة المزيد من المدرات",
                        points = -10,
                        feedback = "خطير. الاستمرار يفاقم الإصابة الكلوية.",
                        nextNodeId = "c_aki_n2"
                    ),
                    ErAction(
                        label = "غسيل كلوي فوري لكل مريض بكرياتينين 3.4",
                        points = -5,
                        feedback = "غير مبرر. الغسيل لاستطبابات طارئة وليس لرقم الكرياتينين وحده.",
                        nextNodeId = "c_aki_n2"
                    ),
                    ErAction(
                        label = "إعطاء صبغة تباين لتصوير الكلى فورًا",
                        points = -10,
                        feedback = "خطأ. الصبغة كلوية السمية وتفاقم الإصابة.",
                        nextNodeId = "c_aki_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_aki_n2",
                text = "البول بدأ يعود لكن البوتاسيوم 6.0 والبيكربونات منخفضة. ما التالي؟",
                vitals = ErVitals(hr = 100, sbp = 106, dbp = 66, rr = 18, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "علاج فرط البوتاسيوم والحماض + مراقبة لصيقة وتقييم حاجة الغسيل",
                        points = 10,
                        feedback = "صحيح. المضاعفات الكيميائية تُعالج فورًا مع مراقبة حاجة الغسيل.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: إصابة كلوية حادة أُزيل مسببها وعولجت مضاعفاتها."
                    ),
                    ErAction(
                        label = "تخريج لأن البول عاد",
                        points = -10,
                        feedback = "خطأ. البوتاسيوم المرتفع والحماض ما زالا خطرين.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج رغم مضاعفات خطيرة."
                    ),
                    ErAction(
                        label = "إعادة الأدوية الكلوية السمية فورًا",
                        points = -10,
                        feedback = "خطأ. إعادة المسبب قبل شفاء الكلى يعيد الإصابة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعادة المسبب مبكرًا."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في الإصابة الكلوية الحادة، أوقف المسبب وأعد الحجم وراقب البوتاسيوم والحماض؛ الغسيل للاستطبابات الطارئة لا لكل ارتفاع كرياتينين."
    ),
    ErCase(
        id = "c_rhabdomyolysis",
        title = "انحلال عضلي Rhabdomyolysis",
        presentation = "رجل 29 سنة، بعد جهد عضلي شديد ومكوث طويل على الأرض، ألم عضلي شديد، بول داكن بلون الشاي، ضعف. CK ‏45000 وحدة/لتر، كرياتينين مرتفع قليلًا.",
        guidelineRef = "Bosch et al., N Engl J Med 2009;361:62-72, doi 10.1056/NEJMra0801327: سوائل وريدية غزيرة مبكرة، ومراقبة البوتاسيوم والكالسيوم والكلى، وفحص متلازمة الحيز.",
        startNodeId = "c_rhabdomyolysis_n1",
        nodes = listOf(
            ErNode(
                id = "c_rhabdomyolysis_n1",
                text = "CK ‏45000 مع بول داكن وخطر قصور كلوي. ما أول علاج؟",
                vitals = ErVitals(hr = 110, sbp = 126, dbp = 78, rr = 20, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "سوائل ملحية وريدية غزيرة فورًا + مراقبة بول وكهارل وكلى",
                        points = 10,
                        feedback = "صحيح. الإماهة الغزيرة المبكرة هي ما يمنع القصور الكلوي في الانحلال.",
                        nextNodeId = "c_rhabdomyolysis_n2"
                    ),
                    ErAction(
                        label = "انتظار نتائج إضافية قبل السوائل",
                        points = -10,
                        feedback = "خطأ. التأخير يزيد خطر القصور الكلوي.",
                        nextNodeId = "c_rhabdomyolysis_n2"
                    ),
                    ErAction(
                        label = "مدرّات فقط بلا سوائل",
                        points = -10,
                        feedback = "خطأ. المدرات بلا إماهة تفاقم نقص الحجم والقصور.",
                        nextNodeId = "c_rhabdomyolysis_n2"
                    ),
                    ErAction(
                        label = "إعطاء كالسيوم وريدي روتيني لتصحيح الكالسيوم المبكر",
                        points = -5,
                        feedback = "خطأ. نقص الكالسيوم المبكر في الانحلال لا يُعالج روتينيًا؛ قد يسبب فرط كالسيوم لاحقًا.",
                        nextNodeId = "c_rhabdomyolysis_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_rhabdomyolysis_n2",
                text = "البول تحسن لكن الألم بإحدى الساقين يزداد مع توتر شديد. ما التالي؟",
                vitals = ErVitals(hr = 112, sbp = 130, dbp = 80, rr = 20, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "قياس ضغط الحيز وجراحة عاجلة عند متلازمة الحيز + استمرار السوائل",
                        points = 10,
                        feedback = "صحيح. ألم متزايد مع توتر بعد انحلال يستدعي نفي متلازمة الحيز جراحيًا.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: انحلال عضلي عولج بالسوائل مع كشف متلازمة الحيز."
                    ),
                    ErAction(
                        label = "مسكنات فقط وانتظار زوال الألم",
                        points = -10,
                        feedback = "خطير. متلازمة الحيز قد تسبب فقد الطرف.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإغفال متلازمة الحيز."
                    ),
                    ErAction(
                        label = "إيقاف السوائل لأن البول تحسن",
                        points = -5,
                        feedback = "خطأ. الانحلال ما زال نشطًا والكلى ما زالت مهددة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للسوائل."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في الانحلال العضلي، السوائل الغزيرة المبكرة تحمي الكلى؛ البوتاسيوم والكالسيوم والحيز يُراقبون لصيقًا."
    ),
    ErCase(
        id = "c_tumor_lysis_syndrome",
        title = "متلازمة انحلال الورم",
        presentation = "شاب 19 سنة، لمفوما عدوانية بدأ علاجًا كيميائيًا أمس، غثيان وضعف، بوتاسيوم 6.2، فوسفات مرتفع، كالسيوم منخفض، حمض بول مرتفع، كرياتينين مرتفع.",
        guidelineRef = "Cairo-Bishop Criteria وBCSH Guideline لمتلازمة انحلال الورم: إماهة غزيرة، وخفض حمض البول بالألوبورينول أو الراسبوريكاز حسب الخطر، ومراقبة الكهارل والغسيل عند الحاجة.",
        startNodeId = "c_tumor_lysis_syndrome_n1",
        nodes = listOf(
            ErNode(
                id = "c_tumor_lysis_syndrome_n1",
                text = "بعد كيميائي، فرط بوتاسيوم وفرط فوسفات وحمض بول مرتفع. ما أول علاج؟",
                vitals = ErVitals(hr = 108, sbp = 116, dbp = 72, rr = 20, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "إماهة وريدية غزيرة + راسبوريكاز أو ألوبورينول حسب الخطر + مراقبة كهارل متكررة",
                        points = 10,
                        feedback = "صحيح. الإماهة وخفض حمض البول يمنعان ترسب البلورات بالكلى.",
                        nextNodeId = "c_tumor_lysis_syndrome_n2"
                    ),
                    ErAction(
                        label = "إعطاء كالسيوم وريدي روتيني لرفع الكالسيوم",
                        points = -10,
                        feedback = "خطأ. إعطاء الكالسيوم مع فرط فوسفات يسبب ترسب فوسفات الكالسيوم بالأنسجة والكلى.",
                        nextNodeId = "c_tumor_lysis_syndrome_n2"
                    ),
                    ErAction(
                        label = "انتظار تحسن تلقائي بلا إماهة",
                        points = -10,
                        feedback = "خطأ. انحلال الورم يتفاقم سريعًا إلى قصور كلوي واضطراب نظم.",
                        nextNodeId = "c_tumor_lysis_syndrome_n2"
                    ),
                    ErAction(
                        label = "مدرّات فقط",
                        points = -5,
                        feedback = "غير كافٍ. المدرات لا تحل فرط حمض البول ولا تحمي الكلى وحدها.",
                        nextNodeId = "c_tumor_lysis_syndrome_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_tumor_lysis_syndrome_n2",
                text = "البوتاسيوم ما زال 6.0 رغم الإماهة والعلاج. ما التالي؟",
                vitals = ErVitals(hr = 100, sbp = 112, dbp = 70, rr = 18, spo2 = 97, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "علاج فرط البوتاسيوم + تقييم غسيل كلوي عاجل عند عدم الاستجابة",
                        points = 10,
                        feedback = "صحيح. فرط البوتاسيوم المقاوم في انحلال الورم استطباب غسيل.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: انحلال ورم عولج بالإماهة وخفض حمض البول ثم الغسيل عند الحاجة."
                    ),
                    ErAction(
                        label = "إعطاء كالسيوم وريدي لتصحيح الكالسيوم المنخفض فقط",
                        points = -10,
                        feedback = "خطأ. الكالسيوم بلا استطباب يفاقم الترسب.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعطاء كالسيوم ضار."
                    ),
                    ErAction(
                        label = "إيقاف المراقبة لأن الأعراض خفيفة",
                        points = -5,
                        feedback = "خطأ. المخبر يتدهور قبل الأعراض.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف المراقبة."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: انحلال الورم حالة مخبرية سريرية تُعالج بالإماهة وخفض حمض البول ومراقبة البوتاسيوم والفوسفات والكالسيوم."
    ),
    ErCase(
        id = "c_thyroid_storm",
        title = "عاصفة درقية",
        presentation = "امرأة 38 سنة، فرط درقية معروف، حرارة 39.8، تسرع قلب 150، رجفان أذيني، ارتباك، إسهال. TSH مكبوت وT4 حر مرتفع جدًا.",
        guidelineRef = "ATA 2016 Guidelines for Hyperthyroidism: في العاصفة الدرقية، حاصرات بيتا، وثيوناميد (بروبيل ثيوراسيل مفضل في العاصفة)، ثم يود بعد الثيوناميد، مع ستيرويد وتبريد ودعم.",
        startNodeId = "c_thyroid_storm_n1",
        nodes = listOf(
            ErNode(
                id = "c_thyroid_storm_n1",
                text = "عاصفة درقية مع حرارة وتسرع ورجفان. ما أول علاج؟",
                vitals = ErVitals(hr = 150, sbp = 138, dbp = 84, rr = 26, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "بروبيل ثيوراسيل + بروبرانولول + تبريد ودعم وسوائل",
                        points = 10,
                        feedback = "صحيح. الثيوناميد يوقف تصنيع الهرمون وحاصرات بيتا تضبط القلب والحرارة.",
                        nextNodeId = "c_thyroid_storm_n2"
                    ),
                    ErAction(
                        label = "يود مشع أو يود فموي فورًا قبل الثيوناميد",
                        points = -10,
                        feedback = "خطير. اليود قبل الثيوناميد يزوّد الغدة بمادة تصنيع الهرمون ويفاقم العاصفة.",
                        nextNodeId = "c_thyroid_storm_n2"
                    ),
                    ErAction(
                        label = "خافض حرارة فقط",
                        points = -5,
                        feedback = "ناقص. الحرارة عرض واحد؛ العاصفة تحتاج علاجًا هرمونيًا وقلبيًا.",
                        nextNodeId = "c_thyroid_storm_n2"
                    ),
                    ErAction(
                        label = "مهدئ فقط",
                        points = -5,
                        feedback = "ناقص. التهدئة لا توقف العاصفة الدرقية.",
                        nextNodeId = "c_thyroid_storm_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_thyroid_storm_n2",
                text = "بعد ساعة من الثيوناميد، ما الإضافة التالية؟",
                vitals = ErVitals(hr = 132, sbp = 130, dbp = 80, rr = 24, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "يود فموي (محلول لوغول) + هيدروكورتيزون + استمرار حاصرات بيتا",
                        points = 10,
                        feedback = "صحيح. اليود بعد الثيوناميد يمنع إفراز الهرمون، والستيرويد يمنع تحوّل T4 إلى T3.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: عاصفة درقية عولجت بالتسلسل الصحيح ثيوناميد ثم يود وستيرويد."
                    ),
                    ErAction(
                        label = "إيقاف حاصرات بيتا لأن النبض انخفض قليلًا",
                        points = -5,
                        feedback = "خطأ. إيقافها المبكر يعيد التسرع والرجفان.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر لحاصرات بيتا."
                    ),
                    ErAction(
                        label = "إعطاء ليفوثيروكسين",
                        points = -10,
                        feedback = "خطأ قاتل. إضافة هرمون درقي في عاصفة درقية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعطاء هرمون درقي في عاصفة."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في العاصفة الدرقية، حاصرات بيتا والثيوناميد أولًا ثم اليود بعدهما؛ اليود قبل الثيوناميد يفاقم العاصفة."
    ),
    ErCase(
        id = "c_myxedema_coma",
        title = "غيبوبة وذمة مخاطية",
        presentation = "امرأة 71 سنة، قصور درقية معروف وتوقفت عن العلاج، نعاس شديد وبرودة، بطء قلب 48، ضغط 92/60، وذمة وجهية، صوديوم 128، حرارة 35.2.",
        guidelineRef = "ATA 2014 Hypothyroidism Guideline: في الغيبوبة الوذمية المخاطية، ليفوثيروكسين وريدي مع هيدروكورتيزون قبله أو معه لنفي قصور كظر مصاحب، وتدفئة سلبية ودعم تنفسي.",
        startNodeId = "c_myxedema_coma_n1",
        nodes = listOf(
            ErNode(
                id = "c_myxedema_coma_n1",
                text = "غيبوبة وذمية مع بطء قلب وانخفاض حرارة. ما أول علاج؟",
                vitals = ErVitals(hr = 48, sbp = 92, dbp = 60, rr = 12, spo2 = 93, gcs = 10),
                actions = listOf(
                    ErAction(
                        label = "هيدروكورتيزون وريدي ثم ليفوثيروكسين وريدي + تدفئة سلبية ودعم",
                        points = 10,
                        feedback = "صحيح. الستيرويد أولًا يحمي من قصور كظر مصاحب قد تكشفه هرمونات الدرقية.",
                        nextNodeId = "c_myxedema_coma_n2"
                    ),
                    ErAction(
                        label = "ليفوثيروكسين فقط بلا ستيرويد",
                        points = -5,
                        feedback = "خطر. هرمون الدرقية بلا ستيرويد قد يفجّر أزمة كظرية عند قصور كظر مصاحب.",
                        nextNodeId = "c_myxedema_coma_n2"
                    ),
                    ErAction(
                        label = "تدفئة نشطة سريعة ببطانيات ساخنة",
                        points = -10,
                        feedback = "خطير. التدفئة السريعة تسبب توسعًا وعائيًا وهبوط ضغط.",
                        nextNodeId = "c_myxedema_coma_n2"
                    ),
                    ErAction(
                        label = "مهدئات للنوم",
                        points = -10,
                        feedback = "خطير. المهدئات تفاقم تثبيط التنفس والوعي.",
                        nextNodeId = "c_myxedema_coma_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_myxedema_coma_n2",
                text = "الوعي بدأ يتحسن لكن التنفس ما زال بطيئًا. ما التالي؟",
                vitals = ErVitals(hr = 54, sbp = 100, dbp = 64, rr = 14, spo2 = 94, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "مراقبة عناية + دعم تنفسي وأكسجين + تصحيح الصوديوم والسكر ببطء",
                        points = 10,
                        feedback = "صحيح. الغيبوبة الوذمية تحتاج عناية ودعمًا تنفسيًا وتصحيحًا حذرًا للكهارل.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: غيبوبة وذمية عولجت بستيرويد ثم هرمون درقي ودعم عناية."
                    ),
                    ErAction(
                        label = "تخريج بعد تحسن بسيط",
                        points = -10,
                        feedback = "خطأ. الغيبوبة الوذمية حالة عناية وقد تنتكس.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج غير آمن."
                    ),
                    ErAction(
                        label = "إيقاف الستيرويد فورًا",
                        points = -5,
                        feedback = "خطأ. الستيرويد يستمر حتى نفي القصور الكظري.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للستيرويد."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في الغيبوبة الوذمية، الستيرويد قبل أو مع هرمون الدرقية يحمي من أزمة كظرية، والتدفئة سلبية لا نشطة سريعة."
    ),
    ErCase(
        id = "c_adrenal_crisis",
        title = "أزمة كظرية حادة",
        presentation = "امرأة 45 سنة، قصور كظر معروف وتوقفت عن الستيرويد أثناء إنتان، غثيان وقيء وألم بطني، ضغط 78/50، سكر 55، صوديوم 128، بوتاسيوم 5.9.",
        guidelineRef = "Endocrine Society 2016 Guideline for Adrenal Insufficiency: في الأزمة الكظرية، هيدروكورتيزون وريدي 100 ملغ فورًا مع سوائل وسكر، بلا انتظار نتيجة الكورتيزول.",
        startNodeId = "c_adrenal_crisis_n1",
        nodes = listOf(
            ErNode(
                id = "c_adrenal_crisis_n1",
                text = "أزمة كظرية مشتبهة مع هبوط ضغط ونقص سكر. ما أول فعل؟",
                vitals = ErVitals(hr = 112, sbp = 78, dbp = 50, rr = 22, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "هيدروكورتيزون 100 ملغ وريدي فورًا + سوائل ملحية ودكستروز",
                        points = 10,
                        feedback = "صحيح. الستيرويد الفوري مع السوائل والسكر هو العلاج المنقذ.",
                        nextNodeId = "c_adrenal_crisis_n2"
                    ),
                    ErAction(
                        label = "انتظار نتيجة الكورتيزول قبل إعطاء الستيرويد",
                        points = -10,
                        feedback = "خطير. التأخير في الأزمة الكظرية قاتل؛ العينة تُؤخذ لكن العلاج لا يُنتظر.",
                        nextNodeId = "c_adrenal_crisis_n2"
                    ),
                    ErAction(
                        label = "مدرّات بول لخفض البوتاسيوم فقط",
                        points = -5,
                        feedback = "ناقص. المدرات لا تعالج القصور الكظري ولا هبوط الضغط.",
                        nextNodeId = "c_adrenal_crisis_n2"
                    ),
                    ErAction(
                        label = "أنسولين لخفض البوتاسيوم",
                        points = -10,
                        feedback = "خطير. الأنسولين يفاقم نقص السكر في الأزمة.",
                        nextNodeId = "c_adrenal_crisis_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_adrenal_crisis_n2",
                text = "الضغط تحسن إلى 105/65 بعد الستيرويد والسوائل. ما التالي؟",
                vitals = ErVitals(hr = 98, sbp = 105, dbp = 65, rr = 18, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "استمرار هيدروكورتيزون بجرعات إجهاد + علاج الإنتان المسبب + تثقيف بجرعة الإجهاد",
                        points = 10,
                        feedback = "صحيح. جرعات الإجهاد تستمر مع علاج المحفّز وتثقيف المريض.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: أزمة كظرية عولجت فورًا ثم بجرعات إجهاد وعلاج المحفّز."
                    ),
                    ErAction(
                        label = "إيقاف الستيرويد فور تحسن الضغط",
                        points = -10,
                        feedback = "خطأ. الإيقاف المبكر يعيد الأزمة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للستيرويد."
                    ),
                    ErAction(
                        label = "تخريج بلا تثقيف بجرعة الإجهاد",
                        points = -5,
                        feedback = "ناقص. المريض سيواجه أزمة جديدة عند أول إنتان.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج بلا تثقيف."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الأزمة الكظرية تُعالج فورًا بالهيدروكورتيزون والسوائل؛ انتظار الكورتيزول يقتل."
    ),
    ErCase(
        id = "c_pheochromocytoma_crisis",
        title = "أزمة ورم القواتم",
        presentation = "رجل 42 سنة، نوبات صداع وخفقان وتعرق، الآن ضغط 220/130، تسرع 132، صداع شديد، سكر مرتفع. تصوير سابق أظهر كتلة كظرية.",
        guidelineRef = "Endocrine Society 2014 Guideline for Pheochromocytoma: الحصار الألفي أولًا ثم الحصار البيتائي، وفي الأزمة، فينتولامين أو نيتروبروسيد، وحاصرات بيتا وحدها ممنوعة.",
        startNodeId = "c_pheochromocytoma_crisis_n1",
        nodes = listOf(
            ErNode(
                id = "c_pheochromocytoma_crisis_n1",
                text = "أزمة ارتفاع ضغط مع كتلة كظرية مشتبهة. ما أول علاج ضغط؟",
                vitals = ErVitals(hr = 132, sbp = 220, dbp = 130, rr = 22, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "حاصرات ألفا (فينتولامين أو فينوكسي بنزامين) أو نيتروبروسيد + مراقبة عناية",
                        points = 10,
                        feedback = "صحيح. الحصار الألفي يعالج التوسع الوعائي الكاتيكولاميني.",
                        nextNodeId = "c_pheochromocytoma_crisis_n2"
                    ),
                    ErAction(
                        label = "حاصرات بيتا وحدها لخفض النبض والضغط",
                        points = -10,
                        feedback = "خطير. بيتا وحده يترك ألفا بلا معارضة ويفاقم ارتفاع الضغط.",
                        nextNodeId = "c_pheochromocytoma_crisis_n2"
                    ),
                    ErAction(
                        label = "مدرّات فقط",
                        points = -5,
                        feedback = "غير كافٍ. المدرات لا تعالج العاصفة الكاتيكولامينية.",
                        nextNodeId = "c_pheochromocytoma_crisis_n2"
                    ),
                    ErAction(
                        label = "انتظار تأكيد التشخيص قبل أي علاج ضغط",
                        points = -10,
                        feedback = "خطأ. الأزمة ارتفاع ضغط طارئ يُعالج فورًا.",
                        nextNodeId = "c_pheochromocytoma_crisis_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_pheochromocytoma_crisis_n2",
                text = "الضغط بدأ ينخفض مع الحصار الألفي لكن التسرع مستمر. ما التالي؟",
                vitals = ErVitals(hr = 118, sbp = 170, dbp = 100, rr = 20, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إضافة حاصرات بيتا بعد الحصار الألفي الكافي + استشارة جراحة الغدد",
                        points = 10,
                        feedback = "صحيح. بعد الألفا، البيتا آمنة وتضبط التسرع، والعلاج النهائي جراحي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: أزمة قواتم عولجت بألفا ثم بيتا مع إحالة جراحية."
                    ),
                    ErAction(
                        label = "إيقاف الحصار الألفي والاكتفاء بالبيتا",
                        points = -10,
                        feedback = "خطأ. إيقاف الألفا يعيد الأزمة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف الحصار الألفي."
                    ),
                    ErAction(
                        label = "إعطاء ميتوكلوبراميد للغثيان",
                        points = -10,
                        feedback = "خطير. الميتوكلوبراميد قد يفجّر أزمة قواتم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بدواء محفّز للأزمة."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في أزمة القواتم، حاصرات بيتا وحدها خطيرة لأنها تترك الألفا بلا معارضة؛ الألفا أولًا دائمًا."
    ),
    ErCase(
        id = "c_alcoholic_ketoacidosis",
        title = "حماض كيتوني كحولي",
        presentation = "رجل 48 سنة، مدمن كحول، قيء مستمر وآلام بطنية بعد يومين بلا طعام وشرب كحولي غزير، تنفس سريع، جفاف. سكر 110، كيتونات قوية، فجوة أنيونية مرتفعة، كحول بالدم منخفض الآن.",
        guidelineRef = "McGuire et al., Emerg Med J 2006;23:417-420, doi 10.1136/emj.2004.017590: في الحماض الكيتوني الكحولي، دكستروز وسوائل وثيامين وكهارل؛ السكر الطبيعي أو المنخفض يميزه عن DKA.",
        startNodeId = "c_alcoholic_ketoacidosis_n1",
        nodes = listOf(
            ErNode(
                id = "c_alcoholic_ketoacidosis_n1",
                text = "حماض كيتوني مع سكر طبيعي وقصة كحول وتجويع. ما أول علاج؟",
                vitals = ErVitals(hr = 112, sbp = 108, dbp = 68, rr = 26, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "دكستروز وريدي + سوائل ملحية + ثيامين قبل أو مع الدكستروز",
                        points = 10,
                        feedback = "صحيح. الدكستروز يوقف الكيتونات والثيامين يمنع اعتلال فيرنيكه.",
                        nextNodeId = "c_alcoholic_ketoacidosis_n2"
                    ),
                    ErAction(
                        label = "أنسولين وريدي كما في DKA",
                        points = -10,
                        feedback = "خطير. الأنسولين مع سكر طبيعي يسبب نقص سكر شديد.",
                        nextNodeId = "c_alcoholic_ketoacidosis_n2"
                    ),
                    ErAction(
                        label = "دكستروز بلا ثيامين",
                        points = -5,
                        feedback = "خطر. الدكستروز بلا ثيامين عند مدمن كحول قد يفجّر فيرنيكه.",
                        nextNodeId = "c_alcoholic_ketoacidosis_n2"
                    ),
                    ErAction(
                        label = "بيكربونات وريدية فورًا",
                        points = -5,
                        feedback = "غير صحيح روتينيًا. الحماض يتحسن بالدكستروز والسوائل.",
                        nextNodeId = "c_alcoholic_ketoacidosis_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_alcoholic_ketoacidosis_n2",
                text = "الفجوة الأنيونية بدأت تضيق والقيء خفّ. ما التالي؟",
                vitals = ErVitals(hr = 98, sbp = 116, dbp = 72, rr = 20, spo2 = 99, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تصحيح البوتاسيوم والمغنيسيوم والفوسفات + تغذية تدريجية ومراقبة انسحاب كحولي",
                        points = 10,
                        feedback = "صحيح. نقص الكهارل شائع ويجب تصحيحه، والانسحاب الكحولي يُراقب.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: حماض كيتوني كحولي عولج بدكستروز وثيامين وسوائل وكهارل."
                    ),
                    ErAction(
                        label = "إيقاف الدكستروز فورًا لأن السكر طبيعي",
                        points = -5,
                        feedback = "خطأ. الدكستروز يستمر حتى إغلاق الفجوة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للدكستروز."
                    ),
                    ErAction(
                        label = "إعطاء أنسولين لتسريع إغلاق الفجوة",
                        points = -10,
                        feedback = "خطأ. الأنسولين غير مطلوب وسكره طبيعي.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بأنسولين غير مبرر."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الحماض الكيتوني الكحولي سكره طبيعي غالبًا؛ علاجه دكستروز وثيامين وسوائل، لا أنسولين."
    ),
    ErCase(
        id = "c_severe_hypokalemia",
        title = "نقص بوتاسيوم شديد",
        presentation = "امرأة 55 سنة، إسهال وقيء منذ أيام مع مدرّات، ضعف عضلي شديد وشلل رخو جزئي، تخطيط: موجات U واضحة وQT طويل. بوتاسيوم 2.1 ملي مكافئ/لتر.",
        guidelineRef = "Viera and Wouk, Am Fam Physician 2015;92:487-495, and Gennari, N Engl J Med 1998;339:451-458: تعويض وريدي بمعدل آمن مع مراقبة قلبية، وتصحيح المغنيسيوم المصاحب، وإيقاف المسبب.",
        startNodeId = "c_severe_hypokalemia_n1",
        nodes = listOf(
            ErNode(
                id = "c_severe_hypokalemia_n1",
                text = "بوتاسيوم 2.1 مع شلل وموجات U. ما أول علاج؟",
                vitals = ErVitals(hr = 88, sbp = 118, dbp = 74, rr = 18, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "كلوريد البوتاسيوم وريدي بمعدل آمن (10 ملي مكافئ/ساعة وريدي محيطي كحد أقصى) مع مراقبة قلبية",
                        points = 10,
                        feedback = "صحيح. المعدل الآمن مع المراقبة يصحح بلا اضطراب نظم.",
                        nextNodeId = "c_severe_hypokalemia_n2"
                    ),
                    ErAction(
                        label = "بوتاسيوم وريدي سريع بجرعة كبيرة دفعة واحدة",
                        points = -10,
                        feedback = "خطير. التعويض السريع يسبب فرط بوتاسيوم مفاجئًا وتوقف قلب.",
                        nextNodeId = "c_severe_hypokalemia_n2"
                    ),
                    ErAction(
                        label = "بوتاسيوم فموي فقط رغم الشلل والتخطيط",
                        points = -10,
                        feedback = "خطأ. الشلل والتغيرات التخطيطية تحتاج تعويضًا وريديًا ومراقبة.",
                        nextNodeId = "c_severe_hypokalemia_n2"
                    ),
                    ErAction(
                        label = "إعطاء أنسولين",
                        points = -10,
                        feedback = "خطأ. الأنسولين يخفض البوتاسيوم أكثر.",
                        nextNodeId = "c_severe_hypokalemia_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_severe_hypokalemia_n2",
                text = "البوتاسيوم ارتفع إلى 2.9 لكن المغنيسيوم منخفض. ما التالي؟",
                vitals = ErVitals(hr = 92, sbp = 120, dbp = 76, rr = 18, spo2 = 98, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "تصحيح المغنيسيوم + استمرار تعويض البوتاسيوم + إيقاف المدر المسبب وعلاج الإسهال",
                        points = 10,
                        feedback = "صحيح. نقص المغنيسيوم يمنع تصحيح البوتاسيوم؛ المسبب يجب إيقافه.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: نقص بوتاسيوم شديد عُوّض بأمان مع تصحيح المغنيسيوم."
                    ),
                    ErAction(
                        label = "إيقاف تعويض البوتاسيوم لأن التحسن بدأ",
                        points = -5,
                        feedback = "خطأ. البوتاسيوم ما زال منخفضًا خطرًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للتعويض."
                    ),
                    ErAction(
                        label = "إعطاء مدرّات إضافية",
                        points = -10,
                        feedback = "خطأ. المدرات تفاقم نقص البوتاسيوم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعادة المسبب."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: نقص البوتاسيوم الشديد يُعوَّض وريديًا بمعدل آمن مع مراقبة قلبية وتصحيح المغنيسيوم؛ التعويض السريع بلا مراقبة خطير."
    ),
    ErCase(
        id = "c_salicylate_poisoning",
        title = "تسمم بالساليسيلات",
        presentation = "شاب 23 سنة، تناول كمية كبيرة من الأسبرين انتحارًا منذ 6 ساعات، فرط تهوية، طنين، غثيان، ارتباك. غازات: قلاء تنفسي ثم حماض أيضي، مستوى ساليسيلات مرتفع.",
        guidelineRef = "NPIS TOXBASE وJuurlink et al., Ann Emerg Med 2015;66:165-181 للساليسيلات: قلونة البول بهدف pH بولي 7.5-8، وغسيل دموي عند الشدة أو تدهور الوعي أو مستوى مرتفع، وتجنب التنبيب بلا فرط تهوية.",
        startNodeId = "c_salicylate_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_salicylate_poisoning_n1",
                text = "تسمم ساليسيلات مع فرط تهوية وارتباك. ما أول علاج؟",
                vitals = ErVitals(hr = 112, sbp = 128, dbp = 78, rr = 30, spo2 = 98, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "بيكربونات الصوديوم وريدي لقلونة الدم والبول + مراقبة غازات ومستوى متكررة",
                        points = 10,
                        feedback = "صحيح. القلونة تحبس الساليسيلات بالدم والبول وتقلل دخولها الدماغ.",
                        nextNodeId = "c_salicylate_poisoning_n2"
                    ),
                    ErAction(
                        label = "تنبيب وتهوية بمعدل طبيعي فورًا لتهدئته",
                        points = -10,
                        feedback = "خطير. فقدان فرط التهوية يرفع CO2 ويزيد دخول الساليسيلات للدماغ.",
                        nextNodeId = "c_salicylate_poisoning_n2"
                    ),
                    ErAction(
                        label = "أسيتازولاميد لقلونة البول",
                        points = -10,
                        feedback = "خطأ. الأسيتازولاميد يسبب حماضًا جهازيًا ويفاقم التسمم رغم قلونة البول.",
                        nextNodeId = "c_salicylate_poisoning_n2"
                    ),
                    ErAction(
                        label = "مدرّات قسرية بلا بيكربونات",
                        points = -5,
                        feedback = "غير كافٍ. المدرات وحدها لا تقلون البول ولا تحمي الدماغ.",
                        nextNodeId = "c_salicylate_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_salicylate_poisoning_n2",
                text = "الوعي يتدهور والمستوى ما زال مرتفعًا رغم القلونة. ما التالي؟",
                vitals = ErVitals(hr = 108, sbp = 122, dbp = 76, rr = 28, spo2 = 98, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "غسيل دموي عاجل + استمرار القلونة ومراقبة عناية",
                        points = 10,
                        feedback = "صحيح. تدهور الوعي أو المستوى المرتفع رغم العلاج استطباب غسيل.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم ساليسيلات عولج بالقلونة ثم الغسيل عند التدهور."
                    ),
                    ErAction(
                        label = "انتظار تحسن تلقائي",
                        points = -10,
                        feedback = "خطأ. تدهور الوعي في تسمم الساليسيلات إنذار وذمة دماغية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بانتظار رغم تدهور الوعي."
                    ),
                    ErAction(
                        label = "إعطاء مهدئ قوي",
                        points = -10,
                        feedback = "خطير. التهدئة تخفي التدهور وتثبط التهوية التعويضية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتهدئة خطيرة."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تسمم الساليسيلات، قلونة البول والغسيل عند الشدة ينقذان؛ التنبيب بلا فرط تهوية يرفع الساليسيلات الدماغية."
    ),
    ErCase(
        id = "c_paracetamol_poisoning",
        title = "تسمم بالباراسيتامول",
        presentation = "فتاة 19 سنة، تناولت 30 غرام باراسيتامول منذ 3 ساعات، الآن بلا أعراض تقريبًا، غثيان خفيف. وظائف كبد طبيعية الآن.",
        guidelineRef = "NPIS/MHRA لعلاج الباراسيتامول: أسيتيل سيستئين NAC وريدي، ويفضل خلال 8 ساعات، بجرعة 150 ملغ/كغ خلال ساعة ثم 50 خلال 4 ساعات ثم 100 خلال 16 ساعة، مع قياس المستوى على مخطط روماك.",
        startNodeId = "c_paracetamol_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_paracetamol_poisoning_n1",
                text = "تناول كبير منذ 3 ساعات بلا أعراض كبدية بعد. ما أول فعل؟",
                vitals = ErVitals(hr = 88, sbp = 122, dbp = 76, rr = 16, spo2 = 99, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "قياس مستوى باراسيتامول بعد 4 ساعات من التناول + بدء NAC وريدي دون انتظار تلف الكبد",
                        points = 10,
                        feedback = "صحيح. NAC المبكر قبل تلف الكبد هو ما يمنع الفشل الكبدي.",
                        nextNodeId = "c_paracetamol_poisoning_n2"
                    ),
                    ErAction(
                        label = "انتظار ظهور يرقان أو ارتفاع إنزيمات قبل العلاج",
                        points = -10,
                        feedback = "خطير. عند ظهور التلف الكبدي يكون NAC أقل فعالية.",
                        nextNodeId = "c_paracetamol_poisoning_n2"
                    ),
                    ErAction(
                        label = "فحم منشط بعد 3 ساعات كعلاج وحيد",
                        points = -5,
                        feedback = "ناقص. الفحم مفيد خلال ساعة أولى فقط ولا يغني عن NAC.",
                        nextNodeId = "c_paracetamol_poisoning_n2"
                    ),
                    ErAction(
                        label = "تخريج لأنها بلا أعراض",
                        points = -10,
                        feedback = "خطير. التسمم الكبدي يتأخر 24-72 ساعة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج رغم تناول كبير."
                    ),
                )
            ),
            ErNode(
                id = "c_paracetamol_poisoning_n2",
                text = "المستوى فوق خط العلاج على مخطط روماك. ما التالي؟",
                vitals = ErVitals(hr = 90, sbp = 120, dbp = 76, rr = 16, spo2 = 99, gcs = 15),
                actions = listOf(
                    ErAction(
                        label = "إكمال بروتوكول NAC الوريدي كاملًا + مراقبة وظائف كبد وتخثر",
                        points = 10,
                        feedback = "صحيح. إكمال NAC مع مراقبة الكبد والتخثر هو العلاج القياسي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم باراسيتامول عولج بـNAC مبكرًا وكاملًا."
                    ),
                    ErAction(
                        label = "إيقاف NAC بعد الجرعة الأولى لأنها تحسنت",
                        points = -5,
                        feedback = "خطأ. البروتوكول كامل حتى زوال الخطر.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر لـNAC."
                    ),
                    ErAction(
                        label = "إعطاء ميثيونين فموي بدل NAC الوريدي",
                        points = -5,
                        feedback = "غير مفضل. NAC الوريدي هو العلاج القياسي في التناول الكبير.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج غير قياسي."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: تسمم الباراسيتامول صامت مبكرًا؛ NAC المبكر يمنع الفشل الكبدي، وانتظار الأعراض يقتل."
    ),
    ErCase(
        id = "c_opioid_overdose",
        title = "جرعة زائدة أفيونية",
        presentation = "رجل 31 سنة، وُجد فاقد الوعي، تنفس 6/د، حدقات دبوسية، SpO2 ‏82%، آثار حقن. لا استجابة للمنبهات.",
        guidelineRef = "CDC وAHA لإدارة الجرعة الأفيونية: نالوكسون معايرة بجرعات صغيرة مع دعم التنفس، وتجنب الإفاقة الكاملة المفاجئة، ومراقبة مطوّلة للأفيونات طويلة المفعول.",
        startNodeId = "c_opioid_overdose_n1",
        nodes = listOf(
            ErNode(
                id = "c_opioid_overdose_n1",
                text = "تنفس 6/د وحدقات دبوسية وفقد وعي. ما أول فعل؟",
                vitals = ErVitals(hr = 58, sbp = 96, dbp = 60, rr = 6, spo2 = 82, gcs = 6),
                actions = listOf(
                    ErAction(
                        label = "نالوكسون وريدي بجرعات صغيرة معايرة + دعم تنفس بالأكسجين والتهوية المساعدة",
                        points = 10,
                        feedback = "صحيح. المعايرة تستعيد التنفس بلا إفاقة مفاجئة ومتلازمة انسحاب.",
                        nextNodeId = "c_opioid_overdose_n2"
                    ),
                    ErAction(
                        label = "نالوكسون جرعة كبيرة دفعة واحدة لإفاقته تمامًا",
                        points = -5,
                        feedback = "غير مثالي. الجرعة الكبيرة تسبب انسحابًا حادًا وقيئًا وشفطًا.",
                        nextNodeId = "c_opioid_overdose_n2"
                    ),
                    ErAction(
                        label = "إعطاء مهدئ لتهدئته",
                        points = -10,
                        feedback = "خطير. المهدئ يفاقم تثبيط التنفس.",
                        nextNodeId = "c_opioid_overdose_n2"
                    ),
                    ErAction(
                        label = "انتظار استيقاظه تلقائيًا",
                        points = -10,
                        feedback = "خطأ. تثبيط التنفس الأفيوني قاتل بلا علاج.",
                        nextNodeId = "c_opioid_overdose_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_opioid_overdose_n2",
                text = "التنفس تحسن إلى 14/د وSpO2 ‏96% بعد نالوكسون. ما التالي؟",
                vitals = ErVitals(hr = 88, sbp = 112, dbp = 70, rr = 14, spo2 = 96, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "مراقبة 4-6 ساعات على الأقل + تسريب نالوكسون عند الحاجة + إحالة لعلاج الإدمان",
                        points = 10,
                        feedback = "صحيح. مفعول النالوكسون أقصر من الأفيونات طويلة المفعول؛ النكس وارد.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: جرعة أفيونية عولجت بنالوكسون معاير ومراقبة مطوّلة."
                    ),
                    ErAction(
                        label = "تخريج فوري بعد التحسن الأولي",
                        points = -10,
                        feedback = "خطأ. النكس التنفسي شائع بعد زوال النالوكسون.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج مبكر رغم خطر النكس."
                    ),
                    ErAction(
                        label = "إعطاء أفيون إضافي لمنع الانسحاب",
                        points = -10,
                        feedback = "خطأ. إضافة أفيون تعيد التثبيط التنفسي.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإضافة أفيون."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في الجرعة الأفيونية، النالوكسون يُعاير لاستعادة التنفس لا للإفاقة الكاملة، والمراقبة مطوّلة."
    ),
    ErCase(
        id = "c_alcohol_withdrawal_dt",
        title = "هذيان ارتعاشي DT بانسحاب كحولي",
        presentation = "رجل 52 سنة، مدمن كحول توقف فجأة منذ يومين، الآن هذيان وهياج وتعرق ورعاش، حرارة 38.6، تسرع 128، هلوسات بصرية.",
        guidelineRef = "NICE CG100 (physical complications of alcohol-use disorders, 2010, updated 2017) وASAM 2020 لانسحاب الكحول: بنزوديازيبينات بجرعات كافية موجّهة بالأعراض، وثيامين، وتصحيح الكهارل، ومراقبة عناية في الهذيان الارتعاشي.",
        startNodeId = "c_alcohol_withdrawal_dt_n1",
        nodes = listOf(
            ErNode(
                id = "c_alcohol_withdrawal_dt_n1",
                text = "هذيان ارتعاشي مع هياج وتسرع وحرارة. ما أول علاج؟",
                vitals = ErVitals(hr = 128, sbp = 148, dbp = 88, rr = 24, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "بنزوديازيبينات وريدية بجرعات كافية موجّهة بالأعراض + ثيامين + سوائل وكهارل",
                        points = 10,
                        feedback = "صحيح. البنزوديازيبينات هي العلاج الأساسي للانسحاب الكحولي الشديد.",
                        nextNodeId = "c_alcohol_withdrawal_dt_n2"
                    ),
                    ErAction(
                        label = "هالوبيريدول وحده لضبط الهذيان",
                        points = -10,
                        feedback = "خطأ. مضادات الذهان وحدها لا تمنع التشنجات وقد تخفض عتبتها.",
                        nextNodeId = "c_alcohol_withdrawal_dt_n2"
                    ),
                    ErAction(
                        label = "تقييده جسديًا فقط بلا أدوية",
                        points = -10,
                        feedback = "خطير. التقييد بلا علاج يفاقم الهياج وفرط الحرارة والانحلال.",
                        nextNodeId = "c_alcohol_withdrawal_dt_n2"
                    ),
                    ErAction(
                        label = "إعطاء كحول لتهدئته",
                        points = -10,
                        feedback = "خطأ. إعادة الكحول تعيد الإدمان ولا تعالج الانسحاب بأمان.",
                        nextNodeId = "c_alcohol_withdrawal_dt_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_alcohol_withdrawal_dt_n2",
                text = "الهياج ما زال شديدًا رغم جرعات بنزوديازيبين أولية. ما التالي؟",
                vitals = ErVitals(hr = 124, sbp = 144, dbp = 86, rr = 22, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "تصعيد جرعات البنزوديازيبين + مراقبة عناية + علاج الحمى والجفاف",
                        points = 10,
                        feedback = "صحيح. الهذيان الارتعاشي يحتاج جرعات كبيرة ومراقبة عناية.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: هذيان ارتعاشي عولج ببنزوديازيبينات كافية وعناية."
                    ),
                    ErAction(
                        label = "إضافة مضاد ذهان بجرعة كبيرة بدل تصعيد البنزوديازيبين",
                        points = -5,
                        feedback = "غير صحيح. مضاد الذهان مساعد فقط ولا يغني عن البنزوديازيبين.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج مساعد بدل الأساسي."
                    ),
                    ErAction(
                        label = "تخريج بعد تهدئة جزئية",
                        points = -10,
                        feedback = "خطأ. الهذيان الارتعاشي قد يتفاقم ويسبب تشنجات.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج غير آمن."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الهذيان الارتعاشي حالة طارئة تُعالج ببنزوديازيبينات كافية وثيامين، لا بمضادات ذهان وحدها."
    ),
    ErCase(
        id = "c_methanol_poisoning",
        title = "تسمم بالميثانول",
        presentation = "رجل 38 سنة، شرب كحولًا مغشوشًا، غثيان واضطراب رؤية وضبابية، تنفس سريع، حماض أيضي شديد بفجوة أنيونية وفجوة أسمولية مرتفعتين.",
        guidelineRef = "EXTRIP لتسمم الميثانول: حجب نازعة الكحول بالفومبيزول أو الإيثانول، وبيكربونات، وغسيل دموي عند الحماض الشديد أو اضطراب الرؤية، وحمض الفوليك كمساعد.",
        startNodeId = "c_methanol_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_methanol_poisoning_n1",
                text = "حماض شديد واضطراب رؤية بعد كحول مغشوش. ما أول علاج؟",
                vitals = ErVitals(hr = 112, sbp = 118, dbp = 74, rr = 28, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "فومبيزول (أو إيثانول إن تعذر) + بيكربونات + قياس مستوى وفجوة أسمولية",
                        points = 10,
                        feedback = "صحيح. حجب الأيض يوقف تكون حمض الفورميك السام للعصب البصري.",
                        nextNodeId = "c_methanol_poisoning_n2"
                    ),
                    ErAction(
                        label = "انتظار تأكيد المستوى قبل أي علاج",
                        points = -10,
                        feedback = "خطير. التأخير يزيد خطر العمى الدائم والوفاة.",
                        nextNodeId = "c_methanol_poisoning_n2"
                    ),
                    ErAction(
                        label = "غسيل معدي",
                        points = -5,
                        feedback = "غير مفيد. الميثانول يُمتص سريعًا والغسيل المعدي لا يزيله.",
                        nextNodeId = "c_methanol_poisoning_n2"
                    ),
                    ErAction(
                        label = "إعطاء ميثانول إضافي",
                        points = -10,
                        feedback = "خطأ. الميثانول هو السم نفسه.",
                        nextNodeId = "c_methanol_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_methanol_poisoning_n2",
                text = "الحماض مستمر والرؤية تتدهور رغم الفومبيزول. ما التالي؟",
                vitals = ErVitals(hr = 108, sbp = 112, dbp = 70, rr = 26, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "غسيل دموي عاجل + حمض فوليك + استمرار حجب الأيض",
                        points = 10,
                        feedback = "صحيح. الغسيل يزيل الميثانول وحمض الفورميك ويصحح الحماض.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم ميثانول عولج بحجب الأيض ثم الغسيل."
                    ),
                    ErAction(
                        label = "الاكتفاء بالفومبيزول رغم تدهور الرؤية",
                        points = -10,
                        feedback = "خطأ. تدهور الرؤية استطباب غسيل عاجل.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتأخير الغسيل رغم تدهور الرؤية."
                    ),
                    ErAction(
                        label = "إعطاء مهدئ للغثيان فقط",
                        points = -5,
                        feedback = "ناقص. الغثيان ليس المشكلة الأساسية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج عرضي فقط."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: تسمم الميثانول يهدد البصر والحياة؛ حجب الأيض والغسيل المبكر يمنعان العمى الدائم."
    ),
    ErCase(
        id = "c_organophosphate_poisoning",
        title = "تسمم بمبيد فوسفات عضوي",
        presentation = "مزارع 40 سنة، تعرض لمبيد حشري، إفرازات فموية غزيرة، تشنجات قصبية، حدقات ضيقة، إسهال، ضعف عضلي، رائحة ثوم. كولين إستراز منخفض.",
        guidelineRef = "WHO/IPCS وإرشادات التسمم بالفوسفات العضوية: إزالة التلوث مع حماية الطاقم، وأتروبين معاير حتى جفاف الإفرازات، وأوكسيمات، وبنزوديازيبينات للتشنجات.",
        startNodeId = "c_organophosphate_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_organophosphate_poisoning_n1",
                text = "إفرازات غزيرة وتشنج قصبي وحدقات ضيقة بعد مبيد. ما أول فعل؟",
                vitals = ErVitals(hr = 88, sbp = 118, dbp = 74, rr = 28, spo2 = 90, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "إزالة الملابس الملوثة وغسل الجلد مع حماية الطاقم + أتروبين وريدي معاير",
                        points = 10,
                        feedback = "صحيح. إزالة التلوث تحمي المريض والطاقم، والأتروبين يعالج التأثيرات المسكارينية.",
                        nextNodeId = "c_organophosphate_poisoning_n2"
                    ),
                    ErAction(
                        label = "علاج المريض بملابسه الملوثة بلا حماية",
                        points = -10,
                        feedback = "خطير. التلوث الثانوي يسمم الطاقم.",
                        nextNodeId = "c_organophosphate_poisoning_n2"
                    ),
                    ErAction(
                        label = "جرعة أتروبين واحدة صغيرة ثم إيقافه",
                        points = -5,
                        feedback = "غير كافٍ. الأتروبين يُعاير حتى جفاف الإفرازات وتحسن الأكسجة.",
                        nextNodeId = "c_organophosphate_poisoning_n2"
                    ),
                    ErAction(
                        label = "إعطاء حاصرات بيتا لخفض النبض",
                        points = -10,
                        feedback = "خطأ. حاصرات بيتا تفاقم التشنج القصبي وبطء القلب.",
                        nextNodeId = "c_organophosphate_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_organophosphate_poisoning_n2",
                text = "الإفرازات خفّت لكن الضعف العضلي مستمر. ما الإضافة؟",
                vitals = ErVitals(hr = 92, sbp = 122, dbp = 76, rr = 22, spo2 = 94, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "براليدوكسيم (أوكسيم) + بنزوديازيبين عند التشنجات + مراقبة تنفسية وعناية",
                        points = 10,
                        feedback = "صحيح. الأوكسيم يعيد تنشيط الكولين إستراز والبنزوديازيبين يعالج التشنجات.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم فوسفات عضوية عولج بإزالة تلوث وأتروبين وأوكسيم."
                    ),
                    ErAction(
                        label = "إيقاف الأتروبين لأن الحدقات توسعت",
                        points = -5,
                        feedback = "خطأ. هدف الأتروبين جفاف الإفرازات والأكسجة لا حجم الحدقة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف الأتروبين لهدف خاطئ."
                    ),
                    ErAction(
                        label = "إعطاء سوكسينيل كولين للتنبيب روتينيًا",
                        points = -10,
                        feedback = "خطير. السوكسينيل قد يطول مفعوله بشدة في تسمم الفوسفات العضوية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بمرخٍ عضلي غير آمن."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تسمم الفوسفات العضوية، الأتروبين يُعاير حتى تجف الإفرازات؛ جرعة واحدة ثابتة لا تكفي."
    ),
    ErCase(
        id = "c_tricyclic_overdose",
        title = "جرعة زائدة بمضادات اكتئاب ثلاثية الحلقات",
        presentation = "شابة 24 سنة، تناولت كمية كبيرة من أميتريبتيلين، نعاس شديد، تخطيط: QRS متسع 140 مللي ثانية، تسرع 128، ضغط 92/60.",
        guidelineRef = "NPIS وAHA 2023 لجرعات التسمم: بيكربونات الصوديوم لتضيق QRS، مع فرط تهوية بهدف pH ‏7.45-7.55، وبنزوديازيبينات للتشنجات، وتجنب الفيزوستيغمين.",
        startNodeId = "c_tricyclic_overdose_n1",
        nodes = listOf(
            ErNode(
                id = "c_tricyclic_overdose_n1",
                text = "QRS متسع 140 مع تسرع وهبوط ضغط بعد أميتريبتيلين. ما أول علاج؟",
                vitals = ErVitals(hr = 128, sbp = 92, dbp = 60, rr = 20, spo2 = 97, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "بيكربونات الصوديوم وريدي + مراقبة تخطيطية مستمرة",
                        points = 10,
                        feedback = "صحيح. البيكربونات تضيّق QRS وتعالج اضطراب النظم والتأثير القلبي.",
                        nextNodeId = "c_tricyclic_overdose_n2"
                    ),
                    ErAction(
                        label = "فيزوستيغمين لعكس التأثير المضاد للكولين",
                        points = -10,
                        feedback = "خطير. الفيزوستيغمين في جرعة ثلاثية الحلقات قد يسبب تشنجات وتوقف قلب.",
                        nextNodeId = "c_tricyclic_overdose_n2"
                    ),
                    ErAction(
                        label = "حاصرات بيتا لخفض التسرع",
                        points = -10,
                        feedback = "خطأ. حاصرات بيتا تفاقم هبوط الضغط واضطراب التوصيل.",
                        nextNodeId = "c_tricyclic_overdose_n2"
                    ),
                    ErAction(
                        label = "فحم منشط كعلاج وحيد رغم النعاس الشديد",
                        points = -10,
                        feedback = "خطير. الفحم مع نقص الوعي يسبب شفطًا بلا حماية طريق هوائي.",
                        nextNodeId = "c_tricyclic_overdose_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_tricyclic_overdose_n2",
                text = "QRS تضيّق قليلًا لكن ما زال متسعًا. ما التالي؟",
                vitals = ErVitals(hr = 118, sbp = 100, dbp = 64, rr = 18, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "استمرار بيكربونات + فرط تهوية مضبوط بهدف pH ‏7.45-7.55 + مراقبة عناية",
                        points = 10,
                        feedback = "صحيح. القلاء يعزز ارتباط الدواء بالبروتين ويضيّق QRS.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: جرعة ثلاثية الحلقات عولجت بالبيكربونات والقلاء المضبوط."
                    ),
                    ErAction(
                        label = "إيقاف البيكربونات لأن التحسن جزئي",
                        points = -5,
                        feedback = "خطأ. التحسن الجزئي يستدعي الاستمرار لا الإيقاف.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للبيكربونات."
                    ),
                    ErAction(
                        label = "إعطاء مضاد اضطراب نظم من الصنف الأول",
                        points = -10,
                        feedback = "خطير. مضادات الصنف الأول تفاقم تضيق QRS واضطراب النظم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بمضاد نظم ضار."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في جرعة ثلاثية الحلقات مع QRS متسع، البيكربونات هي العلاج؛ الفيزوستيغمين ممنوع."
    ),
    ErCase(
        id = "c_lithium_toxicity",
        title = "تسمم بالليثيوم",
        presentation = "رجل 58 سنة، على ليثيوم للاضطراب ثنائي القطب، إسهال وقيء ثم ارتباك ورعاش خشن ونقص وعي، قصور كلوي حاد. مستوى ليثيوم 3.2 ملي مكافئ/لتر.",
        guidelineRef = "EXTRIP 2015 لليثيوم: إيقاف الليثيوم، وإماهة، وغسيل دموي عند الأعراض العصبية الشديدة أو المستوى المرتفع جدًا أو القصور الكلوي، مع مراقبة ارتداد المستوى.",
        startNodeId = "c_lithium_toxicity_n1",
        nodes = listOf(
            ErNode(
                id = "c_lithium_toxicity_n1",
                text = "ليثيوم 3.2 مع ارتباك ورعاش وقصور كلوي. ما أول فعل؟",
                vitals = ErVitals(hr = 102, sbp = 118, dbp = 74, rr = 20, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "إيقاف الليثيوم فورًا + سوائل وريدية + مراقبة مستوى وكلى وأعصاب",
                        points = 10,
                        feedback = "صحيح. إيقاف المصدر والإماهة أولًا مع تحديد حاجة الغسيل.",
                        nextNodeId = "c_lithium_toxicity_n2"
                    ),
                    ErAction(
                        label = "الاستمرار بالليثيوم بجرعة أقل",
                        points = -10,
                        feedback = "خطأ. الاستمرار في تسمم عصبي يفاقم الأذية.",
                        nextNodeId = "c_lithium_toxicity_n2"
                    ),
                    ErAction(
                        label = "مدرّات لتسريع طرح الليثيوم",
                        points = -10,
                        feedback = "خطأ. المدرات تفاقم الجفاف والقصور الكلوي وترفع الليثيوم.",
                        nextNodeId = "c_lithium_toxicity_n2"
                    ),
                    ErAction(
                        label = "فحم منشط",
                        points = -5,
                        feedback = "غير مفيد. الفحم لا يمتص الليثيوم.",
                        nextNodeId = "c_lithium_toxicity_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_lithium_toxicity_n2",
                text = "الوعي يتدهور رغم الإماهة والمستوى ما زال مرتفعًا. ما التالي؟",
                vitals = ErVitals(hr = 98, sbp = 112, dbp = 70, rr = 18, spo2 = 97, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "غسيل دموي عاجل + مراقبة ارتداد المستوى بعد الغسيل",
                        points = 10,
                        feedback = "صحيح. الأعراض العصبية الشديدة مع مستوى مرتفع استطباب غسيل، والارتداد وارد.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم ليثيوم عولج بإيقاف الدواء ثم الغسيل."
                    ),
                    ErAction(
                        label = "انتظار تحسن تلقائي بالإماهة فقط",
                        points = -10,
                        feedback = "خطأ. التدهور العصبي يستدعي غسيلًا عاجلًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتأخير الغسيل رغم التدهور."
                    ),
                    ErAction(
                        label = "إعطاء مهدئ للرعاش فقط",
                        points = -5,
                        feedback = "ناقص. التهدئة لا تزيل الليثيوم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج عرضي فقط."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تسمم الليثيوم العصبي الشديد، الغسيل هو العلاج؛ الإماهة وحدها لا تكفي."
    ),
    ErCase(
        id = "c_serotonin_syndrome",
        title = "متلازمة السيروتونين",
        presentation = "شاب 27 سنة، بدأ مثبط استرداد سيروتونين مع ترامادول، هياج وارتعاش ورمع عضلي بالساقين، تعرق، حرارة 39.2، تسرع، إسهال. ردود فعل مبالغ فيها.",
        guidelineRef = "ACMT ومعايير هانتر: في متلازمة السيروتونين، إيقاف الأدوية السيروتونية، وبنزوديازيبينات، وتبريد، وسيبروهيبتادين عند الحاجة، وتجنب البرومكريبتين والدانترولين والفيزوستيغمين.",
        startNodeId = "c_serotonin_syndrome_n1",
        nodes = listOf(
            ErNode(
                id = "c_serotonin_syndrome_n1",
                text = "هياج ورمع وفرط حرارة بعد أدوية سيروتونية. ما أول علاج؟",
                vitals = ErVitals(hr = 132, sbp = 138, dbp = 84, rr = 24, spo2 = 98, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "إيقاف كل الأدوية السيروتونية فورًا + بنزوديازيبينات + تبريد",
                        points = 10,
                        feedback = "صحيح. إيقاف المسبب مع تهدئة وتبريد هو أساس العلاج.",
                        nextNodeId = "c_serotonin_syndrome_n2"
                    ),
                    ErAction(
                        label = "برومكريبتين لعلاج فرط الحرارة",
                        points = -10,
                        feedback = "خطأ. البرومكريبتين يفاقم متلازمة السيروتونين وهو علاج للمتلازمة الخبيثة للذهان لا هذه.",
                        nextNodeId = "c_serotonin_syndrome_n2"
                    ),
                    ErAction(
                        label = "دانترولين كعلاج أول",
                        points = -5,
                        feedback = "غير مفضل. الدانترولين ليس علاجًا أولًا للسيروتونين وقد يضر.",
                        nextNodeId = "c_serotonin_syndrome_n2"
                    ),
                    ErAction(
                        label = "إعطاء مضاد ذهان إضافي لضبط الهياج",
                        points = -10,
                        feedback = "خطير. مضادات الذهان قد تفاقم فرط الحرارة وتشوش التشخيص.",
                        nextNodeId = "c_serotonin_syndrome_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_serotonin_syndrome_n2",
                text = "الحرارة ما زالت 39 رغم البنزوديازيبينات. ما التالي؟",
                vitals = ErVitals(hr = 128, sbp = 134, dbp = 82, rr = 22, spo2 = 98, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "تبريد فعال + سيبروهيبتادين + مراقبة عناية وتصحيح الكهارل",
                        points = 10,
                        feedback = "صحيح. التبريد والسيبروهيبتادين يدعمان العلاج عند استمرار الأعراض.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: متلازمة سيروتونين عولجت بإيقاف المسبب وبنزوديازيبينات وتبريد."
                    ),
                    ErAction(
                        label = "تقييد جسدي قوي بدل التهدئة",
                        points = -10,
                        feedback = "خطير. التقييد يفاقم فرط الحرارة والانحلال.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتقييد ضار."
                    ),
                    ErAction(
                        label = "إعادة مثبط السيروتونين بجرعة أقل",
                        points = -10,
                        feedback = "خطأ. إعادة المسبب تعيد المتلازمة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعادة المسبب."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: متلازمة السيروتونين تُميَّز بالرمع وفرط المنعكسات؛ علاجها إيقاف المسبب وبنزوديازيبينات وتبريد، لا برومكريبتين."
    ),
    ErCase(
        id = "c_neuroleptic_malignant_syndrome",
        title = "المتلازمة الخبيثة للذهان NMS",
        presentation = "رجل 44 سنة، بدأ هالوبيريدول حديثًا، حرارة 40، صلابة رصاصية، بطء منعكسات، تعرق، تسرع، ارتفاع CK، كرياتينين مرتفع قليلًا.",
        guidelineRef = "International Expert Consensus 2011 للمتلازمة الخبيثة للذهان: إيقاف الدواء المحفّز، ودعم وتبريد، ودانترولين أو برومكريبتين في الشديدة، وبنزوديازيبينات، وتجنب إعادة مضاد الذهان.",
        startNodeId = "c_neuroleptic_malignant_syndrome_n1",
        nodes = listOf(
            ErNode(
                id = "c_neuroleptic_malignant_syndrome_n1",
                text = "حرارة 40 وصلابة رصاصية بعد هالوبيريدول. ما أول فعل؟",
                vitals = ErVitals(hr = 122, sbp = 140, dbp = 88, rr = 24, spo2 = 98, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "إيقاف مضاد الذهان فورًا + سوائل وتبريد ومراقبة عناية",
                        points = 10,
                        feedback = "صحيح. إيقاف المحفّز والدعم هما أساس العلاج.",
                        nextNodeId = "c_neuroleptic_malignant_syndrome_n2"
                    ),
                    ErAction(
                        label = "زيادة جرعة مضاد الذهان لضبط الهياج",
                        points = -10,
                        feedback = "خطير. زيادة المحفّز تفاقم المتلازمة.",
                        nextNodeId = "c_neuroleptic_malignant_syndrome_n2"
                    ),
                    ErAction(
                        label = "إعطاء مضاد ذهان بديل فورًا",
                        points = -10,
                        feedback = "خطأ. كل مضادات الذهان قد تفاقم المتلازمة في المرحلة الحادة.",
                        nextNodeId = "c_neuroleptic_malignant_syndrome_n2"
                    ),
                    ErAction(
                        label = "خافض حرارة فقط",
                        points = -5,
                        feedback = "ناقص. الحرارة عرض واحد؛ الصلابة والانحلال يحتاجان دعمًا نوعيًا.",
                        nextNodeId = "c_neuroleptic_malignant_syndrome_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_neuroleptic_malignant_syndrome_n2",
                text = "الصلابة والحرارة مستمرتان رغم الدعم. ما الإضافة؟",
                vitals = ErVitals(hr = 118, sbp = 136, dbp = 86, rr = 22, spo2 = 98, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "دانترولين أو برومكريبتين + بنزوديازيبينات + مراقبة كلى وCK",
                        points = 10,
                        feedback = "صحيح. في الشديدة، الدانترولين أو البرومكريبتين يدعمان العلاج مع البنزوديازيبينات.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: متلازمة خبيثة للذهان عولجت بإيقاف المحفّز ودعم وعلاج نوعي."
                    ),
                    ErAction(
                        label = "إعادة مضاد الذهان بعد تحسن جزئي",
                        points = -10,
                        feedback = "خطأ. الإعادة المبكرة تسبب نكسًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإعادة مبكرة للمحفّز."
                    ),
                    ErAction(
                        label = "تقييد جسدي فقط",
                        points = -10,
                        feedback = "خطير. التقييد يفاقم فرط الحرارة والانحلال.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتقييد ضار."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: المتلازمة الخبيثة تُميَّز بالصلابة الرصاصية وبطء المنعكسات؛ علاجها إيقاف المحفّز ودعم وتبريد، لا إعادة الدواء."
    ),
    ErCase(
        id = "c_carbon_monoxide_poisoning",
        title = "تسمم بأول أكسيد الكربون",
        presentation = "عائلة بعد عطل مدفأة، صداع وغثيان وارتباك، أحدهم فاقد وعي. كربوكسي هيموغلوبين 32% عند المريض الأشد. SpO2 ‏99% على الهواء.",
        guidelineRef = "Undersea and Hyperbaric Medical Society وAHA: في تسمم CO، أكسجين 100% فورًا، وقياس كربوكسي هيموغلوبين بالدم، والأكسجين مفرط الضغط عند فقد الوعي أو أعراض عصبية أو قلبية أو حمل.",
        startNodeId = "c_carbon_monoxide_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_carbon_monoxide_poisoning_n1",
                text = "تسمم CO مشتبه مع كربوكسي هيموغلوبين 32% وفقد وعي سابق. ما أول علاج؟",
                vitals = ErVitals(hr = 102, sbp = 124, dbp = 78, rr = 20, spo2 = 99, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "أكسجين 100% بقناع غير معيد التنفس فورًا + غازات دم مع كربوكسي هيموغلوبين",
                        points = 10,
                        feedback = "صحيح. الأكسجين العالي يسرّع إزالة CO من الهيموغلوبين.",
                        nextNodeId = "c_carbon_monoxide_poisoning_n2"
                    ),
                    ErAction(
                        label = "الاعتماد على SpO2 ‏99% ونفي التسمم",
                        points = -10,
                        feedback = "خطير. مقياس النبض لا يميز CO عن الأكسجين.",
                        nextNodeId = "c_carbon_monoxide_poisoning_n2"
                    ),
                    ErAction(
                        label = "أكسجين منخفض التدفق فقط",
                        points = -5,
                        feedback = "غير كافٍ. الهدف أكسجين 100% في التسمم الشديد.",
                        nextNodeId = "c_carbon_monoxide_poisoning_n2"
                    ),
                    ErAction(
                        label = "تخريج بعد تحسن الصداع قليلًا",
                        points = -10,
                        feedback = "خطأ. الأعراض العصبية المتأخرة واردة.",
                        nextNodeId = "c_carbon_monoxide_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_carbon_monoxide_poisoning_n2",
                text = "المريض ما زال مرتبكًا رغم الأكسجين 100%. ما التالي؟",
                vitals = ErVitals(hr = 98, sbp = 122, dbp = 76, rr = 18, spo2 = 99, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "تقييم الأكسجين مفرط الضغط + مراقبة قلبية وعصبية + فحص باقي أفراد العائلة",
                        points = 10,
                        feedback = "صحيح. الأعراض العصبية المستمرة استطباب أكسجين مفرط الضغط، والتعرض جماعي.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم CO عولج بأكسجين 100% ثم تقييم أكسجين مفرط الضغط."
                    ),
                    ErAction(
                        label = "إيقاف الأكسجين لأن SpO2 طبيعية",
                        points = -10,
                        feedback = "خطأ. SpO2 لا تعكس CO.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف الأكسجين رغم التسمم."
                    ),
                    ErAction(
                        label = "إعطاء مهدئ للارتباك فقط",
                        points = -5,
                        feedback = "ناقص. التهدئة تخفي التدهور العصبي.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج عرضي فقط."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: مقياس النبض يكذب في تسمم CO؛ العلاج أكسجين 100% وقياس الدم، والأكسجين مفرط الضغط للحالات الشديدة."
    ),
    ErCase(
        id = "c_cyanide_poisoning",
        title = "تسمم بالسيانيد",
        presentation = "رجل 41 سنة، بعد حريق مغلق، فقد وعي ثم تحسن جزئي، لاكتات 12، حماض أيضي شديد، ضغط منخفض، رائحة لوز مر غير واضحة. تعرض سابق لدخان.",
        guidelineRef = "ACMT وإرشادات تسمم السيانيد: هيدروكسوكوبالامين 5 غرام وريدي هو الترياق المفضل، مع ثيوسلفات الصوديوم، وتجنب نتريت الصوديوم عند تسمم CO المصاحب.",
        startNodeId = "c_cyanide_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_cyanide_poisoning_n1",
                text = "حماض بلاكتات عالٍ بعد حريق مع اشتباه سيانيد. ما أول علاج؟",
                vitals = ErVitals(hr = 118, sbp = 88, dbp = 56, rr = 26, spo2 = 94, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "هيدروكسوكوبالامين 5 غرام وريدي + أكسجين 100% + دعم ضغط",
                        points = 10,
                        feedback = "صحيح. الترياق المفضل يرتبط بالسيانيد ويزيله بأمان نسبي.",
                        nextNodeId = "c_cyanide_poisoning_n2"
                    ),
                    ErAction(
                        label = "نتريت الصوديوم فورًا كعلاج أول",
                        points = -10,
                        feedback = "خطير. النتريت يسبب ميتهيموغلوبينيميا ويضاعف نقص الأكسجة مع CO المصاحب.",
                        nextNodeId = "c_cyanide_poisoning_n2"
                    ),
                    ErAction(
                        label = "انتظار تأكيد مستوى السيانيد قبل الترياق",
                        points = -10,
                        feedback = "خطأ. مستوى السيانيد يتأخر ولا يُنتظر في الاشتباه القوي.",
                        nextNodeId = "c_cyanide_poisoning_n2"
                    ),
                    ErAction(
                        label = "بيكربونات فقط",
                        points = -5,
                        feedback = "ناقص. البيكربونات لا تزيل السيانيد.",
                        nextNodeId = "c_cyanide_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_cyanide_poisoning_n2",
                text = "اللاكتات بدأت تنخفض بعد الترياق. ما التالي؟",
                vitals = ErVitals(hr = 104, sbp = 108, dbp = 66, rr = 20, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "ثيوسلفات الصوديوم كمساعد + مراقبة لاكتات وغازات وعناية",
                        points = 10,
                        feedback = "صحيح. الثيوسلفات تساعد إزالة السيانيد والمراقبة تكشف النكس.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم سيانيد عولج بهيدروكسوكوبالامين وثيوسلفات."
                    ),
                    ErAction(
                        label = "إيقاف المراقبة لأن اللاكتات تحسنت قليلًا",
                        points = -5,
                        feedback = "خطأ. النكس وارد والمراقبة مستمرة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإيقاف مبكر للمراقبة."
                    ),
                    ErAction(
                        label = "إعطاء نتريت الصوديوم الآن",
                        points = -10,
                        feedback = "خطأ. النتريت غير مفضل مع CO المصاحب.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بنتريت ضار."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تسمم السيانيد بعد حريق، الهيدروكسوكوبالامين هو الترياق المفضل؛ النتريت خطير مع CO المصاحب."
    ),
    ErCase(
        id = "c_iron_poisoning",
        title = "تسمم بالحديد",
        presentation = "طفلة 3 سنوات، تناولت أقراص حديد من والدتها منذ ساعتين، قيء دموي وإسهال وألم بطني، نعاس. حديد مصل مرتفع جدًا.",
        guidelineRef = "NPIS وACMT لتسمم الحديد: غسل أمعاء كامل عند أقراص مرئية بالصورة، وديفيروكسامين وريدي في التسمم الشديد، والفحم غير مفيد للحديد.",
        startNodeId = "c_iron_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_iron_poisoning_n1",
                text = "طفلة بتناول حديد وقيء دموي وحديد مصل مرتفع. ما أول تدبير؟",
                vitals = ErVitals(hr = 122, sbp = 98, dbp = 62, rr = 24, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "صورة بطن + غسل أمعاء كامل + سوائل ومراقبة حديد وغازات",
                        points = 10,
                        feedback = "صحيح. الأقراص المرئية تُزال بالغسل الكامل قبل امتصاصها.",
                        nextNodeId = "c_iron_poisoning_n2"
                    ),
                    ErAction(
                        label = "فحم منشط كعلاج وحيد",
                        points = -10,
                        feedback = "خطأ. الفحم لا يمتص الحديد ولا يمنع امتصاصه.",
                        nextNodeId = "c_iron_poisoning_n2"
                    ),
                    ErAction(
                        label = "تحريض القيء",
                        points = -10,
                        feedback = "خطير. القيء المحرّض يسبب شفطًا ولا يزيل الحديد بفعالية.",
                        nextNodeId = "c_iron_poisoning_n2"
                    ),
                    ErAction(
                        label = "تخريج لأن القيء أخرج بعض الأقراص",
                        points = -10,
                        feedback = "خطأ. التسمم بالحديد له طور كامن ثم تدهور كبدي.",
                        nextNodeId = "c_iron_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_iron_poisoning_n2",
                text = "الطفلة تتدهور مع حماض ونقص ضغط رغم الغسل. ما التالي؟",
                vitals = ErVitals(hr = 128, sbp = 88, dbp = 54, rr = 28, spo2 = 95, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "ديفيروكسامين وريدي + دعم ضغط وسوائل وعناية",
                        points = 10,
                        feedback = "صحيح. الديفيروكسامين يخلّب الحديد في التسمم الشديد.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم حديد عولج بغسل أمعاء وديفيروكسامين."
                    ),
                    ErAction(
                        label = "الاكتفاء بالسوائل بلا ديفيروكسامين رغم التدهور",
                        points = -10,
                        feedback = "خطأ. التدهور استطباب خلّاب.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتأخير الديفيروكسامين."
                    ),
                    ErAction(
                        label = "إعطاء حديد إضافي لتعويض القيء",
                        points = -10,
                        feedback = "خطأ قاتل. إضافة حديد في تسمم حديد.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بإضافة حديد."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: الفحم لا يمتص الحديد؛ في التسمم الشديد، غسل الأمعاء والديفيروكسامين هما العلاج."
    ),
    ErCase(
        id = "c_benzodiazepine_overdose",
        title = "جرعة زائدة ببنزوديازيبينات",
        presentation = "شاب 26 سنة، تناول كمية كبيرة من ألبرازولام وحده، نعاس شديد، تنفس 10/د، SpO2 ‏93%، ضغط طبيعي، بلا تناول مشترك آخر مؤكد.",
        guidelineRef = "NPIS وAHA لجرعة البنزوديازيبينات: الدعم التنفسي هو الأساس، والفلومازينيل محفوف بالخطر عند الاستخدام المزمن أو التناول المشترك، ولا يُستخدم روتينيًا.",
        startNodeId = "c_benzodiazepine_overdose_n1",
        nodes = listOf(
            ErNode(
                id = "c_benzodiazepine_overdose_n1",
                text = "نعاس شديد وتنفس بطيء بعد ألبرازولام وحده. ما أول تدبير؟",
                vitals = ErVitals(hr = 72, sbp = 118, dbp = 74, rr = 10, spo2 = 93, gcs = 11),
                actions = listOf(
                    ErAction(
                        label = "دعم تنفسي بالأكسجين والتهوية المساعدة ومراقبة",
                        points = 10,
                        feedback = "صحيح. الدعم هو العلاج؛ معظم الحالات تتحسن بالدعم وحده.",
                        nextNodeId = "c_benzodiazepine_overdose_n2"
                    ),
                    ErAction(
                        label = "فلومازينيل روتيني فورًا لإفاقته",
                        points = -10,
                        feedback = "خطير. الفلومازينيل قد يسبب تشنجات وانسحابًا خاصة مع استخدام مزمن أو تناول مشترك غير مؤكد.",
                        nextNodeId = "c_benzodiazepine_overdose_n2"
                    ),
                    ErAction(
                        label = "فحم منشط رغم النعاس الشديد بلا حماية طريق هوائي",
                        points = -10,
                        feedback = "خطير. الفحم مع نقص الوعي يسبب شفطًا.",
                        nextNodeId = "c_benzodiazepine_overdose_n2"
                    ),
                    ErAction(
                        label = "مهدئ إضافي لتهدئته",
                        points = -10,
                        feedback = "خطأ. مهدئ إضافي يفاقم تثبيط التنفس.",
                        nextNodeId = "c_benzodiazepine_overdose_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_benzodiazepine_overdose_n2",
                text = "التنفس تحسن إلى 14/د وSpO2 ‏97% بالدعم. ما التالي؟",
                vitals = ErVitals(hr = 80, sbp = 120, dbp = 76, rr = 14, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "مراقبة لصيقة + فحص تناول مشترك (أفيونات، كحول) + إحالة نفسية",
                        points = 10,
                        feedback = "صحيح. التناول المشترك هو ما يحوّل الجرعة إلى قاتلة ويجب نفيه.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: جرعة بنزوديازيبين عولجت بالدعم مع نفي تناول مشترك."
                    ),
                    ErAction(
                        label = "تخريج فوري بعد تحسن التنفس",
                        points = -5,
                        feedback = "غير آمن. النكس وارد والتناول المشترك لم يُنفَ بعد.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج قبل نفي التناول المشترك."
                    ),
                    ErAction(
                        label = "إعطاء فلومازينيل الآن لأنه تحسن",
                        points = -10,
                        feedback = "خطأ. الفلومازينيل لا يُعطى بعد التحسن بالدعم.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بفلومازينيل غير مبرر."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: جرعة البنزوديازيبينات وحدها تُعالج بالدعم؛ الفلومازينيل الروتيني قد يسبب تشنجات وانسحابًا."
    ),
    ErCase(
        id = "c_cocaine_toxicity",
        title = "تسمم بالكوكايين",
        presentation = "رجل 29 سنة، بعد تعاطي كوكايين، هياج شديد، حرارة 39.5، تسرع 150، ضغط 190/110، ألم صدري، تعرق. تخطيط: تسرع جيبي بلا ارتفاع ST واضح.",
        guidelineRef = "AHA 2023 لسمية المنبهات: بنزوديازيبينات أولًا للهياج وارتفاع الضغط والتسرع، وتجنب حاصرات بيتا وحدها، وتبريد، وعلاج الألم الصدري كاحتشاء عند الحاجة.",
        startNodeId = "c_cocaine_toxicity_n1",
        nodes = listOf(
            ErNode(
                id = "c_cocaine_toxicity_n1",
                text = "هياج وفرط حرارة وارتفاع ضغط بعد كوكايين. ما أول علاج؟",
                vitals = ErVitals(hr = 150, sbp = 190, dbp = 110, rr = 26, spo2 = 98, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "بنزوديازيبينات وريدية + تبريد + مراقبة قلبية",
                        points = 10,
                        feedback = "صحيح. البنزوديازيبينات تهدئ الهياج وتخفض الضغط والتسرع والحرارة.",
                        nextNodeId = "c_cocaine_toxicity_n2"
                    ),
                    ErAction(
                        label = "حاصرات بيتا وحدها لخفض النبض والضغط",
                        points = -10,
                        feedback = "خطير. بيتا وحده يترك ألفا بلا معارضة ويفاقم ارتفاع الضغط والتشنج الوعائي.",
                        nextNodeId = "c_cocaine_toxicity_n2"
                    ),
                    ErAction(
                        label = "تقييد جسدي قوي فقط",
                        points = -10,
                        feedback = "خطير. التقييد يفاقم فرط الحرارة والانحلال.",
                        nextNodeId = "c_cocaine_toxicity_n2"
                    ),
                    ErAction(
                        label = "مضاد ذهان وحده",
                        points = -5,
                        feedback = "غير صحيح كأول. مضاد الذهان لا يعالج فرط النشاط الودي وقد يفاقم الحرارة.",
                        nextNodeId = "c_cocaine_toxicity_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_cocaine_toxicity_n2",
                text = "الألم الصدري مستمر رغم البنزوديازيبينات. ما التالي؟",
                vitals = ErVitals(hr = 138, sbp = 175, dbp = 105, rr = 22, spo2 = 98, gcs = 14),
                actions = listOf(
                    ErAction(
                        label = "أسبرين + نيتروغليسرين + تقييم احتشاء مع تجنب حاصرات بيتا",
                        points = 10,
                        feedback = "صحيح. الألم الصدري بعد كوكايين يُدبَّر كاحتشاء مع تجنب بيتا وحده.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم كوكايين عولج ببنزوديازيبينات وتبريد وتدبير صدري آمن."
                    ),
                    ErAction(
                        label = "إعطاء حاصرات بيتا الآن لأن الضغط ما زال مرتفعًا",
                        points = -10,
                        feedback = "خطأ. بيتا وحده ما زال ممنوعًا.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بحاصرات بيتا وحدها."
                    ),
                    ErAction(
                        label = "تخريج لأن التخطيط بلا ارتفاع ST",
                        points = -10,
                        feedback = "خطأ. احتشاء الكوكايين قد يكون بلا ST واضح ويحتاج مراقبة.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتخريج رغم ألم صدري."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تسمم الكوكايين، البنزوديازيبينات أولًا؛ حاصرات بيتا وحدها تترك ألفا بلا معارضة."
    ),
    ErCase(
        id = "c_ethylene_glycol_poisoning",
        title = "تسمم بالإيثيلين غليكول",
        presentation = "رجل 47 سنة، شرب سائل تبريد (مضاد تجمد)، غثيان وارتباك، حماض أيضي شديد بفجوة أنيونية، فجوة أسمولية مرتفعة، بلورات أكسالات كالسيوم بالبول، كرياتينين مرتفع.",
        guidelineRef = "EXTRIP 2023 للإيثيلين غليكول: فومبيزول لحجب الأيض، وبيكربونات، وغسيل دموي عند الحماض الشديد أو القصور الكلوي، وثيامين وبيريدوكسين كمساعدات.",
        startNodeId = "c_ethylene_glycol_poisoning_n1",
        nodes = listOf(
            ErNode(
                id = "c_ethylene_glycol_poisoning_n1",
                text = "حماض شديد وبلورات أكسالات بعد مضاد تجمد. ما أول علاج؟",
                vitals = ErVitals(hr = 112, sbp = 118, dbp = 74, rr = 26, spo2 = 97, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "فومبيزول + بيكربونات + قياس فجوة أسمولية ومستوى",
                        points = 10,
                        feedback = "صحيح. حجب الأيض يوقف تكون حمض الغليكوليك والأكساليك السامين.",
                        nextNodeId = "c_ethylene_glycol_poisoning_n2"
                    ),
                    ErAction(
                        label = "انتظار تأكيد المستوى قبل العلاج",
                        points = -10,
                        feedback = "خطير. التأخير يزيد الأذية الكلوية.",
                        nextNodeId = "c_ethylene_glycol_poisoning_n2"
                    ),
                    ErAction(
                        label = "إعطاء إيثانول بلا مراقبة كعلاج وحيد",
                        points = -5,
                        feedback = "غير مثالي. الإيثانول بديل عند تعذر الفومبيزول لكنه يحتاج مراقبة دقيقة.",
                        nextNodeId = "c_ethylene_glycol_poisoning_n2"
                    ),
                    ErAction(
                        label = "مدرّات فقط",
                        points = -5,
                        feedback = "غير كافٍ. المدرات لا توقف الأيض السام.",
                        nextNodeId = "c_ethylene_glycol_poisoning_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_ethylene_glycol_poisoning_n2",
                text = "القصور الكلوي يتفاقم والحماض مستمر رغم الفومبيزول. ما التالي؟",
                vitals = ErVitals(hr = 108, sbp = 112, dbp = 70, rr = 24, spo2 = 97, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "غسيل دموي عاجل + ثيامين وبيريدوكسين + استمرار الفومبيزول",
                        points = 10,
                        feedback = "صحيح. الغسيل يزيل السم ونواتج الأيض ويصحح الحماض.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم إيثيلين غليكول عولج بحجب الأيض ثم الغسيل."
                    ),
                    ErAction(
                        label = "الاكتفاء بالفومبيزول رغم تفاقم الكلى",
                        points = -10,
                        feedback = "خطأ. تفاقم الكلى والحماض استطباب غسيل.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتأخير الغسيل."
                    ),
                    ErAction(
                        label = "إعطاء كالسيوم وريدي لتعويض الأكسالات",
                        points = -5,
                        feedback = "غير مبرر. الكالسيوم لا يعالج التسمم وقد يفاقم الترسب.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بعلاج غير موجّه."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: تسمم مضاد التجمد يدمر الكلى؛ حجب الأيض والغسيل المبكر يمنعان القصور الدائم."
    ),
    ErCase(
        id = "c_theophylline_toxicity",
        title = "تسمم بالثيوفيلين",
        presentation = "امرأة 34 سنة، تناولت كمية كبيرة من ثيوفيلين، غثيان وقيء وتسرع 145، رعاش، نوبة تشنج واحدة، بوتاسيوم 2.8، مستوى ثيوفيلين مرتفع جدًا.",
        guidelineRef = "NPIS وEXTRIP theophylline, Clin Toxicol 2015 للثيوفيلين: فحم منشط متعدد الجرعات، وبنزوديازيبينات للتشنجات، وحاصرات بيتا قصيرة المفعول لاضطراب النظم، وغسيل أو تروية دموية في الشديدة، وتجنب الفينيتوين.",
        startNodeId = "c_theophylline_toxicity_n1",
        nodes = listOf(
            ErNode(
                id = "c_theophylline_toxicity_n1",
                text = "تسرع وتشنج ونقص بوتاسيوم بعد ثيوفيلين. ما أول علاج؟",
                vitals = ErVitals(hr = 145, sbp = 128, dbp = 80, rr = 24, spo2 = 98, gcs = 12),
                actions = listOf(
                    ErAction(
                        label = "بنزوديازيبينات للتشنج + فحم منشط متعدد الجرعات إن كان الطريق الهوائي محميًا + تصحيح بوتاسيوم",
                        points = 10,
                        feedback = "صحيح. البنزوديازيبينات هي مضاد التشنج في هذا التسمم والفحم يقلل الامتصاص المستمر.",
                        nextNodeId = "c_theophylline_toxicity_n2"
                    ),
                    ErAction(
                        label = "فينيتوين للتشنجات",
                        points = -10,
                        feedback = "خطأ. الفينيتوين غير فعال في تشنجات الثيوفيلين وقد يفاقمها.",
                        nextNodeId = "c_theophylline_toxicity_n2"
                    ),
                    ErAction(
                        label = "حاصرات بيتا طويلة المفعول فورًا",
                        points = -5,
                        feedback = "غير مثالي. حاصرات بيتا قصيرة المفعول هي المفضلة عند اضطراب النظم.",
                        nextNodeId = "c_theophylline_toxicity_n2"
                    ),
                    ErAction(
                        label = "إعطاء ثيوفيلين إضافي لعلاج الربو",
                        points = -10,
                        feedback = "خطأ قاتل. إضافة الدواء السام.",
                        nextNodeId = "c_theophylline_toxicity_n2"
                    ),
                )
            ),
            ErNode(
                id = "c_theophylline_toxicity_n2",
                text = "المستوى ما زال مرتفعًا جدًا والتسرع مستمر رغم العلاج. ما التالي؟",
                vitals = ErVitals(hr = 140, sbp = 124, dbp = 78, rr = 22, spo2 = 98, gcs = 13),
                actions = listOf(
                    ErAction(
                        label = "غسيل دموي أو تروية دموية عاجلة + مراقبة عناية",
                        points = 10,
                        feedback = "صحيح. المستوى المرتفع جدًا مع أعراض شديدة استطباب إزالة خارج الجسم.",
                        nextNodeId = null,
                        endOutcome = "أُنجزت الحالة: تسمم ثيوفيلين عولج بفحم وبنزوديازيبينات ثم إزالة خارج الجسم."
                    ),
                    ErAction(
                        label = "الاكتفاء بالفحم رغم المستوى المرتفع جدًا",
                        points = -10,
                        feedback = "خطأ. الفحم وحده لا يكفي في التسمم الشديد.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بتأخير الإزالة خارج الجسم."
                    ),
                    ErAction(
                        label = "إعطاء مدرّات لتسريع الطرح",
                        points = -5,
                        feedback = "غير مجدٍ. الثيوفيلين لا يُطرح بالمدرات بفعالية.",
                        nextNodeId = null,
                        endOutcome = "انتهت الحالة بمدرات غير مجدية."
                    ),
                )
            ),
        ),
        endSummary = "الدرس: في تسمم الثيوفيلين، الفحم متعدد الجرعات والبنزوديازيبينات للتشنجات؛ الفينيتوين لا يفيد."
    )
)
