package com.netflix.spinnaker.keel.echo

import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.echo.model.EchoNotification
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface EchoService {
  @POST("/notifications")
  suspend fun sendNotification(
    @Body notification: EchoNotification,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  )
}
