package com.gallery.sync.data.local.entity

/**
 * The cloud provider a media item or folder was indexed from.
 *
 * Google Photos is a Pro-gated *feature* (see `BillingRepository` in v0.3.0); the presence of the
 * enum constant here gates nothing on its own.
 */
enum class MediaSource {
    ONEDRIVE,
    GOOGLE_PHOTOS
}
