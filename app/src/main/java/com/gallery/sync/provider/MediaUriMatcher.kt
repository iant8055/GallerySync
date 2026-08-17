package com.gallery.sync.provider

/** The kinds of URI [MediaContentProvider] serves. */
internal enum class MediaRoute {
    MEDIA_DIRECTORY,
    MEDIA_ITEM,
    UNKNOWN
}

/**
 * Routes a provider URI to a [MediaRoute].
 *
 * Deliberately pure: it takes the already-split, already-decoded path segments rather than a
 * `Uri`, and uses neither `android.net.Uri` nor `android.content.UriMatcher`. Both are empty
 * stubs in local unit tests, so keeping the routing decision out of them is what makes this
 * logic testable on the JVM without a device.
 */
internal object MediaUriMatcher {

    fun match(pathSegments: List<String>): MediaRoute = when {
        pathSegments.size == 1 && pathSegments[0] == MediaContract.PATH_MEDIA ->
            MediaRoute.MEDIA_DIRECTORY

        pathSegments.size == 2 && pathSegments[0] == MediaContract.PATH_MEDIA ->
            MediaRoute.MEDIA_ITEM

        else -> MediaRoute.UNKNOWN
    }

    /**
     * The item id from a [MediaRoute.MEDIA_ITEM] path, or `null` for any other route.
     *
     * The segment is returned exactly as given. `Uri.pathSegments` hands the provider decoded
     * segments, so a percent-encoded id such as `onedrive%3AABC` arrives here as `onedrive:ABC`.
     */
    fun itemIdFrom(pathSegments: List<String>): String? =
        if (match(pathSegments) == MediaRoute.MEDIA_ITEM) pathSegments[1] else null
}
