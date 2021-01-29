package com.netflix.spinnaker.keel.lemur

import retrofit2.http.GET
import retrofit2.http.Path

interface LemurService {
  @GET("/api/1/certificates/name/{name}")
  suspend fun certificateByName(@Path("name") name: String) : LemurCertificateResponse
}
