package sy.medpro.watani.data

import java.io.File

data class LibraryDownloadResult(
    val file: File? = null,
    val errorMessage: String? = null,
    val success: Boolean
) {
    companion object {
        fun ok(file: File) = LibraryDownloadResult(file = file, success = true)
        fun fail(message: String) = LibraryDownloadResult(errorMessage = message, success = false)
    }
}

enum class HashVerification { VERIFIED, SIZE_ONLY, UNKNOWN_FORMAT }
