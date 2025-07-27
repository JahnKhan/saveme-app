package com.app.saveme.data

import retrofit2.http.GET
import retrofit2.http.Query

interface DigitalTwinApiService {
    @GET("get_digital_twin.php")
    suspend fun getDigitalTwin(@Query("token") token: String): DigitalTwinResponse
}

data class DigitalTwinResponse(
    val text: String
) 