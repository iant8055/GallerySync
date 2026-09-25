package com.gallery.sync.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A refused key check names the box to look at, instead of one message for every mistake. */
class S3RefusalTest {

    @Test
    fun `the error code is read out of an S3 error document`() {
        val body = "<?xml version=\"1.0\"?><Error><Code>SignatureDoesNotMatch</Code><Message>x</Message></Error>"
        assertEquals("SignatureDoesNotMatch", s3ErrorCode(body))
    }

    @Test
    fun `no code, or an empty body, gives null`() {
        assertNull(s3ErrorCode(""))
        assertNull(s3ErrorCode("<Error><Message>nope</Message></Error>"))
        assertNull(s3ErrorCode("<Code></Code>"))
    }

    @Test
    fun `each known code names what to check`() {
        assertTrue(S3CompatibleCloud.rejectionMessage("InvalidAccessKeyId").contains("key ID"))
        assertTrue(S3CompatibleCloud.rejectionMessage("SignatureDoesNotMatch").contains("secret key or the region"))
        assertTrue(S3CompatibleCloud.rejectionMessage("AuthorizationHeaderMalformed").contains("region"))
        assertTrue(S3CompatibleCloud.rejectionMessage("AccessDenied").contains("bucket"))
    }

    @Test
    fun `an unknown code is shown so it can be reported, and no code keeps the plain message`() {
        assertEquals(
            "The store rejected those keys. (Mystery)",
            S3CompatibleCloud.rejectionMessage("Mystery")
        )
        assertEquals("The store rejected those keys.", S3CompatibleCloud.rejectionMessage(null))
    }
}
