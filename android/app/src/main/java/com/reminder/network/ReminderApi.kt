package com.reminder.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ReminderApi {
    @GET("reminders")
    suspend fun list(): List<ReminderDto>

    @POST("reminders")
    suspend fun create(@Body body: CreateReminderRequest): ReminderDto

    @PUT("reminders/{id}")
    suspend fun update(@Path("id") id: Int, @Body body: UpdateReminderRequest): ReminderDto

    @DELETE("reminders/{id}")
    suspend fun delete(@Path("id") id: Int)

    @POST("reminders/{id}/fire")
    suspend fun fire(@Path("id") id: Int, @Body body: FireOccurrenceRequest): OccurrenceDto

    @GET("occurrences/pending")
    suspend fun pendingOccurrences(): List<OccurrenceDto>

    @POST("occurrences/{id}/check")
    suspend fun check(@Path("id") id: Int): OccurrenceDto
}
