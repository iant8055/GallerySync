package com.gallery.sync.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies a Play purchase's signature against a Base64-encoded RSA public key — the local
 * stand-in for a server-side receipt check that [PlayBillingRepository] uses since GallerySync has
 * no backend at all (see TASK-026). Any failure (a bad key, a malformed signature, anything) reads
 * as "not verified", never as "assume it's fine" — this must fail closed.
 *
 * A pure function with no Android or Billing Library dependency, deliberately: [Purchase] is not
 * constructible off-device, but the two strings this actually checks are, so the class extracts them
 * first and hands over only what a unit test can also produce. `java.util.Base64` rather than
 * `android.util.Base64` for the same reason — it is the standard JDK implementation, available at
 * this project's `minSdk 26`, and runs in a plain JVM test.
 */
internal fun verifyPurchaseSignature(
    signedData: String,
    signatureBase64: String,
    publicKeyBase64: String
): Boolean = try {
    val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    val signature = Signature.getInstance("SHA1withRSA")
    signature.initVerify(publicKey)
    signature.update(signedData.toByteArray())
    signature.verify(Base64.getDecoder().decode(signatureBase64))
} catch (e: Exception) {
    false
}
