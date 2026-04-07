package com.malikhw.catgirlsdisplay

import com.google.gson.annotations.SerializedName

data class NekosResponse(
    @SerializedName("images") val images: List<NekoImage>
)

data class NekoImage(
    @SerializedName("id") val id: String,
    @SerializedName("artist") val artist: String?,
    @SerializedName("nsfw") val nsfw: Boolean,
    @SerializedName("tags") val tags: List<String>,
    @SerializedName("likes") val likes: Int?,
    @SerializedName("favorites") val favorites: Int?,
    @SerializedName("uploader") val uploader: Uploader?,
    @SerializedName("createdAt") val createdAt: String?
)

data class Uploader(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String
)

data class SavedImage(
    val id: String,
    val path: String,
    val timestamp: Long
)
