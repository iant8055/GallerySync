package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The stored OneDrive listing must come back exactly, and must never come back wrong.
 *
 * A listing that decodes wrongly would offer files that are not there, so anything that is not exactly
 * what the encoder wrote has to decode to null, which costs one read of the drive.
 */
class DriveListingCodecTest {

    private val listing = DriveListing(
        folders = linkedMapOf(
            "car show" to linkedMapOf(
                "IMG_1.jpg" to RemoteFileRef("id-1", 3_000_000L, "image/jpeg", 1_789_000_000_000L),
                "clip.mp4" to RemoteFileRef("id-2", 29_256_747L, "video/mp4", 0L)
            ),
            "Camera" to linkedMapOf(
                "unknown size.jpg" to RemoteFileRef("id-3", null, "application/octet-stream", 5L)
            )
        ),
        couldNotList = false
    )

    private fun roundTrip(original: DriveListing, at: Long = 42L, root: String = "Samsung Gallery/DCIM") =
        DriveListingCodec.decode(DriveListingCodec.encode(original, at, root))

    @Test
    fun `a listing comes back exactly as it went in`() {
        val decoded = roundTrip(listing)
        assertNotNull(decoded)
        assertEquals(listing, decoded!!.listing)
        assertEquals(42L, decoded.listedAtMillis)
        assertEquals("Samsung Gallery/DCIM", decoded.root)
    }

    @Test
    fun `the order of folders and files is kept`() {
        val decoded = roundTrip(listing)!!
        assertEquals(listOf("car show", "Camera"), decoded.listing.folders.keys.toList())
        assertEquals(listOf("IMG_1.jpg", "clip.mp4"), decoded.listing.folders.getValue("car show").keys.toList())
    }

    @Test
    fun `a file whose size OneDrive did not report stays unreported`() {
        val decoded = roundTrip(listing)!!
        assertNull(decoded.listing.folders.getValue("Camera").getValue("unknown size.jpg").sizeBytes)
    }

    @Test
    fun `names with tabs, newlines, backslashes and non-Latin letters survive`() {
        val awkward = DriveListing(
            folders = linkedMapOf(
                "a\tb\nc\\d" to linkedMapOf(
                    "na\tme\\n.jpg" to RemoteFileRef("i\\d", 1L, "image/jpeg", 2L),
                    "写真 🌸.jpg" to RemoteFileRef("id", 2L, "image/jpeg", 3L)
                )
            ),
            couldNotList = true
        )
        assertEquals(awkward, roundTrip(awkward)!!.listing)
    }

    @Test
    fun `the flag that some folder could not be read is kept`() {
        assertEquals(true, roundTrip(listing.copy(couldNotList = true))!!.listing.couldNotList)
    }

    @Test
    fun `an empty listing round-trips`() {
        assertEquals(DriveListing(emptyMap(), false), roundTrip(DriveListing(emptyMap(), false))!!.listing)
    }

    @Test
    fun `text that is not a stored listing decodes to null`() {
        assertNull(DriveListingCodec.decode(""))
        assertNull(DriveListingCodec.decode("not a listing"))
        assertNull(DriveListingCodec.decode("v9\t1\t0\troot\n"))
    }

    @Test
    fun `a file cut off part way decodes to null rather than to a short list`() {
        val text = DriveListingCodec.encode(listing, 1L, "root")
        val cut = text.substring(0, text.length - 12)
        assertNull(DriveListingCodec.decode(cut))
    }

    @Test
    fun `a file line before any folder line is rejected`() {
        assertNull(DriveListingCodec.decode("v1\t1\t0\troot\nR\ta.jpg\tid\t1\timage/jpeg\t0\n"))
    }
}
