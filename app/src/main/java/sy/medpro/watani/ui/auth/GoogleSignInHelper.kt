package sy.medpro.watani.ui.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Team 1 — shared Google sign-in (extracted verbatim from SignInGate so
 * the welcome screen, the profile screen and the activation prompt all
 * use the same flow). Sign-in is OPTIONAL everywhere now: it is offered
 * for activation and profile, never as an entry wall.
 *
 * Returns Result.success on a completed Firebase sign-in, or
 * Result.failure with a human-readable message otherwise. The Firebase
 * sign-in callback is bridged with suspendCancellableCoroutine; callers
 * must call from a coroutine scope.
 */
object GoogleSignInHelper {

    private const val SERVER_CLIENT_ID =
        "660952704333-8cdodgfk7prfj3sv70725fu6oq9h0mjr.apps.googleusercontent.com"

    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetSignInWithGoogleOption.Builder(
                serverClientId = SERVER_CLIENT_ID
            ).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential =
                    com.google.firebase.auth.GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                suspendCancellableCoroutine { continuation ->
                    FirebaseAuth.getInstance().signInWithCredential(authCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                continuation.resume(Result.success(Unit))
                            } else {
                                continuation.resume(
                                    Result.failure(
                                        Exception(
                                            "فشل تسجيل الدخول عبر Firebase: ${task.exception?.localizedMessage}"
                                        )
                                    )
                                )
                            }
                        }
                }
            } else {
                Result.failure(Exception("نوع الاعتماد غير متوافق."))
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            Result.failure(Exception("تم إلغاء تسجيل الدخول أو حدث خطأ: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(Exception("خطأ غير متوقع: ${e.localizedMessage}"))
        }
    }
}
