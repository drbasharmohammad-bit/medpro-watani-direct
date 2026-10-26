package sy.medpro.watani.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface WataniApi {
    @GET("manifest.json")
    suspend fun getManifest(): Response<ManifestResponse>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://watani-content.pages.dev/v1/"
    }
}
