package com.gallery.sync.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which addresses the in-app page viewer may load itself, and which it must hand to the browser.
 *
 * The viewer shows real web pages, so this predicate is the only thing standing between a link on
 * one of them and the app quietly becoming a browser. The near-miss hosts are the cases worth
 * pinning: a check written as `contains` or `endsWith` passes every one of them.
 */
class SupportLinksTest {

    @Test
    fun ourOwnPagesLoadInsideTheApp() {
        assertTrue(SupportLinks.staysInApp(SupportPage.PRIVACY_POLICY.url))
        assertTrue(SupportLinks.staysInApp(SupportPage.DELETE_ACCOUNT.url))
        assertTrue(SupportLinks.staysInApp(SupportPage.HOW_TO_GUIDE.url))
    }

    @Test
    fun aLinkBetweenOurPagesAndAnAnchorStayInside() {
        assertTrue(SupportLinks.staysInApp("https://iant8055.github.io/GallerySync/delete-account.html"))
        assertTrue(SupportLinks.staysInApp("https://iant8055.github.io/GallerySync/privacy-policy.html#changes"))
    }

    @Test
    fun otherSitesGoToTheBrowser() {
        assertFalse(SupportLinks.staysInApp("https://account.live.com/consent/Manage"))
        assertFalse(SupportLinks.staysInApp("https://privacy.microsoft.com/privacystatement"))
        assertFalse(SupportLinks.staysInApp("https://github.com/iant8055/GallerySync/issues"))
    }

    @Test
    fun aLookalikeHostDoesNotPass() {
        assertFalse(SupportLinks.staysInApp("https://iant8055.github.io.example.com/GallerySync/x.html"))
        assertFalse(SupportLinks.staysInApp("https://eviliant8055.github.io/GallerySync/x.html"))
        assertFalse(SupportLinks.staysInApp("https://example.com/https://iant8055.github.io/GallerySync/x.html"))
    }

    @Test
    fun anotherPathOnTheSameHostDoesNotPass() {
        assertFalse(SupportLinks.staysInApp("https://iant8055.github.io/other-project/page.html"))
        assertFalse(SupportLinks.staysInApp("https://iant8055.github.io/GallerySyncX/page.html"))
    }

    @Test
    fun plainHttpAndOtherSchemesDoNotPass() {
        assertFalse(SupportLinks.staysInApp("http://iant8055.github.io/GallerySync/privacy-policy.html"))
        assertFalse(SupportLinks.staysInApp("file:///sdcard/GallerySync/privacy-policy.html"))
        assertFalse(SupportLinks.staysInApp("javascript:alert(1)"))
    }

    @Test
    fun garbageDoesNotPassAndDoesNotThrow() {
        assertFalse(SupportLinks.staysInApp(""))
        assertFalse(SupportLinks.staysInApp("not a url at all"))
        assertFalse(SupportLinks.staysInApp("https://"))
    }
}
