package com.gallery.sync.domain.open

import org.junit.Assert.assertEquals
import org.junit.Test

/** TASK-028's decisions table, one row at a time. */
class OpenFilePlanTest {

    private val mb = 1000L * 1000

    private fun first(
        onPhone: Boolean = false,
        setting: OpenNotOnPhone = OpenNotOnPhone.CLOUD,
        links: Boolean = true,
        download: Boolean = true,
        network: NetworkKind = NetworkKind.WIFI,
        mobileAllowed: Boolean = false,
        size: Long = 5 * mb
    ) = OpenFilePlan.firstStep(onPhone, setting, links, download, network, mobileAllowed, size)

    @Test
    fun `a file on the phone opens there whatever the setting, the network or the Cloud`() {
        OpenNotOnPhone.entries.forEach { setting ->
            NetworkKind.entries.forEach { network ->
                assertEquals(OpenStep.OpenOnPhone, first(onPhone = true, setting = setting, network = network, links = false))
            }
        }
    }

    @Test
    fun `not on the phone with Open in the Cloud asks the Cloud for a link`() {
        assertEquals(OpenStep.FetchLink, first())
        assertEquals(OpenStep.FetchLink, first(network = NetworkKind.MOBILE))
    }

    @Test
    fun `the default is Open in the Cloud`() {
        assertEquals(OpenNotOnPhone.CLOUD, OpenNotOnPhone.DEFAULT)
        assertEquals(OpenNotOnPhone.CLOUD, OpenNotOnPhone.fromNameOrDefault(null))
        assertEquals(OpenNotOnPhone.CLOUD, OpenNotOnPhone.fromNameOrDefault("nonsense"))
        assertEquals(OpenNotOnPhone.DOWNLOAD, OpenNotOnPhone.fromNameOrDefault("DOWNLOAD"))
    }

    @Test
    fun `a Cloud with no links at all downloads with no question, as B2 and IDrive e2 do`() {
        assertEquals(OpenStep.Download(askFirst = false), first(links = false))
    }

    @Test
    fun `Download temporarily downloads even when the Cloud has links`() {
        assertEquals(OpenStep.Download(askFirst = false), first(setting = OpenNotOnPhone.DOWNLOAD))
    }

    @Test
    fun `no connection at all refuses before anything is asked`() {
        assertEquals(OpenStep.Refuse(RefuseReason.NO_NETWORK), first(network = NetworkKind.NONE))
        assertEquals(
            OpenStep.Refuse(RefuseReason.NO_NETWORK),
            first(setting = OpenNotOnPhone.DOWNLOAD, network = NetworkKind.NONE)
        )
    }

    @Test
    fun `on mobile data with the switch off a download is refused`() {
        assertEquals(
            OpenStep.Refuse(RefuseReason.MOBILE_DATA_OFF),
            first(setting = OpenNotOnPhone.DOWNLOAD, network = NetworkKind.MOBILE, mobileAllowed = false)
        )
    }

    @Test
    fun `on mobile data with the switch on a small file downloads without asking`() {
        assertEquals(
            OpenStep.Download(askFirst = false),
            first(setting = OpenNotOnPhone.DOWNLOAD, network = NetworkKind.MOBILE, mobileAllowed = true)
        )
    }

    @Test
    fun `exactly 750 MB on mobile data does not ask and one byte more does`() {
        val line = OpenFilePlan.ASK_ON_MOBILE_ABOVE_BYTES
        assertEquals(750 * mb, line)
        assertEquals(
            OpenStep.Download(askFirst = false),
            OpenFilePlan.downloadStep(true, NetworkKind.MOBILE, mobileDataAllowed = true, sizeBytes = line)
        )
        assertEquals(
            OpenStep.Download(askFirst = true),
            OpenFilePlan.downloadStep(true, NetworkKind.MOBILE, mobileDataAllowed = true, sizeBytes = line + 1)
        )
    }

    @Test
    fun `on Wi-Fi a large file never asks`() {
        assertEquals(
            OpenStep.Download(askFirst = false),
            OpenFilePlan.downloadStep(true, NetworkKind.WIFI, mobileDataAllowed = false, sizeBytes = 5_000 * mb)
        )
    }

    @Test
    fun `a Cloud that cannot send the file back is refused rather than tried`() {
        assertEquals(
            OpenStep.Refuse(RefuseReason.CANNOT_DOWNLOAD),
            first(setting = OpenNotOnPhone.DOWNLOAD, download = false)
        )
        assertEquals(
            OpenStep.Refuse(RefuseReason.CANNOT_DOWNLOAD),
            OpenFilePlan.downloadStep(false, NetworkKind.WIFI, mobileDataAllowed = true, sizeBytes = mb)
        )
    }

    @Test
    fun `Download after a failed link obeys the same mobile data rules`() {
        assertEquals(
            OpenStep.Refuse(RefuseReason.MOBILE_DATA_OFF),
            OpenFilePlan.downloadStep(true, NetworkKind.MOBILE, mobileDataAllowed = false, sizeBytes = mb)
        )
    }
}
