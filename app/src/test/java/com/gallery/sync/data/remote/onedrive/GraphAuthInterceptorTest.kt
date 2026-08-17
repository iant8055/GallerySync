package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the OkHttp interceptor that attaches the Graph bearer token.
 *
 * The behaviour worth pinning is the null-token path: the interceptor must proceed WITHOUT the
 * header rather than throw. Throwing would surface as an opaque `IOException`, which the repository
 * maps to `RemoteError.Network` — destroying the distinction from `RemoteError.Unauthorized`.
 */
class GraphAuthInterceptorTest {

    private val request = Request.Builder()
        .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
        .build()

    @Test
    fun `a present token is attached as a bearer authorization header`() {
        val tokenProvider = tokenProviderReturning("eyJ0eXAiOiJKV1QifQ.payload.signature")
        val chain = chainFor(request)

        GraphAuthInterceptor(tokenProvider).intercept(chain)

        assertEquals(
            "Bearer eyJ0eXAiOiJKV1QifQ.payload.signature",
            capturedRequest(chain).header("Authorization")
        )
    }

    @Test
    fun `a null token proceeds without the authorization header and does not throw`() {
        val tokenProvider = tokenProviderReturning(null)
        val chain = chainFor(request)

        // No assertion needed on the absence of a throw: the test failing would show it.
        GraphAuthInterceptor(tokenProvider).intercept(chain)

        assertNull(capturedRequest(chain).header("Authorization"))
    }

    @Test
    fun `a null token still proceeds down the chain`() {
        val chain = chainFor(request)

        GraphAuthInterceptor(tokenProviderReturning(null)).intercept(chain)

        verify(chain).proceed(any())
    }

    @Test
    fun `the original request is otherwise untouched when a token is attached`() {
        val chain = chainFor(request)

        GraphAuthInterceptor(tokenProviderReturning("token-123")).intercept(chain)

        val forwarded = capturedRequest(chain)
        assertEquals(request.url, forwarded.url)
        assertEquals(request.method, forwarded.method)
    }

    @Test
    fun `an empty-string token is still attached verbatim`() {
        // Only null means "not signed in". An empty token is a provider bug, and the resulting 401
        // is more diagnosable than silently dropping the header.
        val chain = chainFor(request)

        GraphAuthInterceptor(tokenProviderReturning("")).intercept(chain)

        // OkHttp trims header values as it stores them, so the prefix's trailing space is gone by
        // the time it can be read back. What this pins is that the header is present at all.
        assertEquals("Bearer", capturedRequest(chain).header("Authorization"))
    }

    @Test
    fun `the interceptor returns the response the chain produced`() {
        val chain = chainFor(request)

        val response = GraphAuthInterceptor(tokenProviderReturning("token-123")).intercept(chain)

        assertEquals(200, response.code)
    }

    // ---------- helpers ----------

    private fun tokenProviderReturning(token: String?): OneDriveTokenProvider =
        mock { onBlocking { getAccessToken() } doReturn token }

    private fun chainFor(request: Request): Interceptor.Chain {
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            Response.Builder()
                .request(invocation.getArgument(0))
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody(null))
                .build()
        }
        return chain
    }

    private fun capturedRequest(chain: Interceptor.Chain): Request {
        val captor = argumentCaptor<Request>()
        verify(chain).proceed(captor.capture())
        return captor.firstValue
    }
}
