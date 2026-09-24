package com.gallery.sync.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * Unit tests for [verifyPurchaseSignature].
 *
 * A real RSA key pair is generated once per test — the whole point of extracting this into a pure
 * function was to be able to sign and verify for real, rather than trust that the code merely
 * compiles against a `Purchase` object nothing here can construct.
 */
class PurchaseSignatureTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(data: String): String {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair.private)
        signature.update(data.toByteArray())
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    @Test
    fun `a signature made with the matching private key verifies`() {
        val data = """{"orderId":"GPA.1234","productId":"pro_unlock"}"""

        assertTrue(verifyPurchaseSignature(data, sign(data), publicKeyBase64))
    }

    @Test
    fun `data altered after signing fails to verify`() {
        // This is the entire point of the check: Play signed one payload, and something downstream
        // — a rooted device, a modified response — is presenting a different one.
        val original = """{"orderId":"GPA.1234","productId":"pro_unlock"}"""
        val tampered = """{"orderId":"GPA.1234","productId":"pro_unlock","purchaseState":9}"""

        assertFalse(verifyPurchaseSignature(tampered, sign(original), publicKeyBase64))
    }

    @Test
    fun `a signature from a different key pair fails to verify`() {
        // Simulates a forged purchase: signed by *something*, just not by Play's actual key.
        val data = """{"orderId":"GPA.1234","productId":"pro_unlock"}"""
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val signature = Signature.getInstance("SHA1withRSA").apply {
            initSign(otherKeyPair.private)
            update(data.toByteArray())
        }
        val forgedSignature = Base64.getEncoder().encodeToString(signature.sign())

        assertFalse(verifyPurchaseSignature(data, forgedSignature, publicKeyBase64))
    }

    @Test
    fun `a malformed public key fails closed rather than throwing`() {
        val data = """{"orderId":"GPA.1234"}"""

        assertFalse(verifyPurchaseSignature(data, sign(data), "not-a-valid-key"))
    }

    @Test
    fun `a malformed signature fails closed rather than throwing`() {
        val data = """{"orderId":"GPA.1234"}"""

        assertFalse(verifyPurchaseSignature(data, "not-a-valid-signature", publicKeyBase64))
    }

    @Test
    fun `an empty signature fails closed`() {
        val data = """{"orderId":"GPA.1234"}"""

        assertFalse(verifyPurchaseSignature(data, "", publicKeyBase64))
    }
}
