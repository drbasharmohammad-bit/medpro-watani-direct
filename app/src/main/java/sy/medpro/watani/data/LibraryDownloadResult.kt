package sy.medpro.watani.data

import java.io.File

data class LibraryDownloadResult(
    val file: File? = null,
    val errorMessage: String? = null,
    val success: Boolean,
    /** True when the failure is a gateway 401/403: activation is lost
     * (cleared + wiped + prompt event already fired by the repository). */
    val authRevoked: Boolean = false
) {
    companion object {
        fun ok(file: File) = LibraryDownloadResult(file = file, success = true)
        fun fail(message: String) = LibraryDownloadResult(errorMessage = message, success = false)
        fun failAuth(message: String) =
            LibraryDownloadResult(errorMessage = message, success = false, authRevoked = true)
    }
}

enum class HashVerification { VERIFIED, SIZE_ONLY, UNKNOWN_FORMAT }
