package com.netflix.spinnaker.keel.ec2

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response.error

val RETROFIT_NOT_FOUND = HttpException(
  error<Any>(404, "".toResponseBody("application/json".toMediaTypeOrNull()))
)
