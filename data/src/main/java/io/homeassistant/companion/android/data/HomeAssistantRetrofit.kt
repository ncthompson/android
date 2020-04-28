package io.homeassistant.companion.android.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.homeassistant.companion.android.domain.url.UrlRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.security.cert.CertificateFactory

class HomeAssistantRetrofit @Inject constructor(urlRepository: UrlRepository) {
    companion object {
        private const val LOCAL_HOST = "http://localhost/"
    }

    var cert = """-----BEGIN CERTIFICATE-----
<FOR TESTING>
-----END CERTIFICATE-----
-----BEGIN PRIVATE KEY-----
<FOR TESTING>
-----END PRIVATE KEY-----"""

    val key = HeldCertificate.decode(cert)
    val factory = CertificateFactory.getInstance("X.509")

    private val handshake = HandshakeCertificates.Builder()
        .addPlatformTrustedCertificates()
        .heldCertificate(key, key.certificate)
        .build()

    val retrofit: Retrofit = Retrofit
        .Builder()
        .addConverterFactory(
            JacksonConverterFactory.create(
                ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                    .registerKotlinModule()
            )
        )
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .addInterceptor {
                    return@addInterceptor if (it.request().url.toString().contains(LOCAL_HOST)) {
                        val newRequest = runBlocking {
                            it.request().newBuilder()
                                .url(it.request().url.toString().replace(LOCAL_HOST, urlRepository.getUrl().toString()))
                                .build()
                        }
                        it.proceed(newRequest)
                    } else {
                        it.proceed(it.request())
                    }
                }
                .sslSocketFactory(handshake.sslSocketFactory(), handshake.trustManager)
                .build()
        )
        .baseUrl(LOCAL_HOST)
        .build()
}
