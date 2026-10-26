package sy.medpro.watani.data

data class DownloadedSectionEntity(
    val sectionPath: String,
    val version: String,
    val hash: String,
    val count: Int,
    val downloadedAt: Long
)

data class QuestionEntity(
    val id: Long = 0,
    val sectionPath: String,
    val questionId: String?,
    val text: String,
    val optionsJson: String,
    val optionLabelsJson: String?,
    val correctRaw: String?,
    val normalizedCorrectIndex: Int,
    val explanation: String?,
    val yearsJson: String?,
    val source: String?,
    val session: String?,
    val topicsJson: String?
)
