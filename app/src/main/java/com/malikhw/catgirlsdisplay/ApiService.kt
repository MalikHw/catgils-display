package com.malikhw.catgirlsdisplay

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface NekosApi {
    @GET("v1/random/image")
    suspend fun getRandomImages(
        @Query("nsfw") nsfw: String? = null,
        @Query("count") count: Int = 1
    ): NekosResponse
    
    @POST("v1/images/search")
    suspend fun searchImages(@Body request: SearchRequest): NekosResponse
    
    @GET("v1/images/{id}")
    suspend fun getImage(@Path("id") id: String): ImageResponse
}

data class SearchRequest(
    val nsfw: Boolean? = null,
    val tags: List<String>? = null,
    val limit: Int = 1,
    val skip: Int = 0
)

data class ImageResponse(
    val image: NekoImage
)

object ApiClient {
    private const val BASE_URL = "https://nekos.moe/api/"
    
    val service: NekosApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NekosApi::class.java)
    }
}
