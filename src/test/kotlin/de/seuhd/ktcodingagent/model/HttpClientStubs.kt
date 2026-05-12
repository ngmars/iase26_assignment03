package de.seuhd.ktcodingagent.model

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.net.http.HttpResponse.PushPromiseHandler
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import kotlin.jvm.optionals.getOrNull

/**
 * Base class for test [HttpClient] implementations.
 *
 * `java.net.http.HttpClient` is an abstract class with eight non-functional accessor methods
 * (cookie handler, connect timeout, redirect policy, etc.). Implementing them in every test
 * stub doubles the file size and obscures the test logic. Subclasses only need to override
 * the generic [send].
 */
internal abstract class TestHttpClient : HttpClient() {
    override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
    override fun connectTimeout(): Optional<Duration> = Optional.empty()
    override fun followRedirects(): Redirect = Redirect.NEVER
    override fun proxy(): Optional<ProxySelector> = Optional.empty()
    override fun sslContext(): SSLContext = SSLContext.getDefault()
    override fun sslParameters(): SSLParameters = SSLParameters()
    override fun authenticator(): Optional<Authenticator> = Optional.empty()
    override fun version(): Version = Version.HTTP_1_1
    override fun executor(): Optional<Executor> = Optional.empty()

    override fun <T> sendAsync(
        request: HttpRequest,
        responseBodyHandler: BodyHandler<T>
    ): CompletableFuture<HttpResponse<T>> = try {
        CompletableFuture.completedFuture(send(request, responseBodyHandler))
    } catch (e: Throwable) {
        CompletableFuture.failedFuture(e)
    }

    override fun <T> sendAsync(
        request: HttpRequest,
        responseBodyHandler: BodyHandler<T>,
        pushPromiseHandler: PushPromiseHandler<T>?
    ): CompletableFuture<HttpResponse<T>> = sendAsync(request, responseBodyHandler)
}

/** Records the outgoing request so a test can assert against it. */
internal class RequestCapture {
    lateinit var uri: URI
    lateinit var method: String
    var body: String = ""
}

/**
 * [HttpClient] stub that records the request into [capture] and returns a canned response.
 *
 * The response body is always interpreted as `String` (Ollama's `/api/generate` returns
 * JSON text); the body handler is otherwise ignored.
 */
internal class StubHttpClient(
    private val capture: RequestCapture,
    private val cannedBody: String,
    private val cannedStatus: Int
) : TestHttpClient() {
    override fun <T> send(request: HttpRequest, responseBodyHandler: BodyHandler<T>): HttpResponse<T> {
        capture.uri = request.uri()
        capture.method = request.method()
        capture.body = request.bodyPublisher().getOrNull()?.let { publisher ->
            val baos = ByteArrayOutputStream()
            publisher.subscribe(object : Flow.Subscriber<ByteBuffer> {
                override fun onSubscribe(s: Flow.Subscription) { s.request(Long.MAX_VALUE) }
                override fun onNext(item: ByteBuffer) {
                    val bytes = ByteArray(item.remaining())
                    item.get(bytes)
                    baos.write(bytes)
                }
                override fun onError(throwable: Throwable) {}
                override fun onComplete() {}
            })
            baos.toString(Charsets.UTF_8)
        } ?: ""
        @Suppress("UNCHECKED_CAST")
        return StubHttpResponse(request, cannedStatus, cannedBody) as HttpResponse<T>
    }
}

/** [HttpClient] stub whose `send` always throws [IOException]. */
internal class FailingHttpClient : TestHttpClient() {
    override fun <T> send(request: HttpRequest, responseBodyHandler: BodyHandler<T>): HttpResponse<T> =
        throw IOException("connection refused")
}

private class StubHttpResponse(
    private val request: HttpRequest,
    private val status: Int,
    private val body: String
) : HttpResponse<String> {
    override fun statusCode() = status
    override fun request() = request
    override fun previousResponse() = Optional.empty<HttpResponse<String>>()
    override fun headers(): HttpHeaders? = HttpHeaders.of(emptyMap()) { _, _ -> true }
    override fun body() = body
    override fun sslSession() = Optional.empty<SSLSession>()
    override fun uri(): URI? = request.uri()
    override fun version() = HttpClient.Version.HTTP_1_1
}
