package sy.medpro.watani.data

import java.io.File

data class LibraryDownloadResult(
    val file: File? = null,
    val errorMessage: String? = null,
    val success: Boolean
) {
    companion object {
        fun ok(file: File) = LibraryDownloadResult(file = file, success = true)
        // NOTE (detector b9): a former authRevoked flag was removed —
        // no code ever read it. A gateway 401/403 failure is signalled
        // by the activationLost event (prompt + wipe), fired by the
        // repository before returning this failure; the flag added
        // nothing over fail().
        fun fail(message: String) = LibraryDownloadResult(errorMessage = message, success = false)
    }
}

enum class HashVerification { VERIFIED, SIZE_ONLY, UNKNOWN_FORMAT }
