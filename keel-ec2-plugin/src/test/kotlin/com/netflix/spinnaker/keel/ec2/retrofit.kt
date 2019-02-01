package com.netflix.spinnaker.keel.ec2

import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response.error

val RETROFIT_NOT_FOUND = HttpException(
  error<Any>(404, ResponseBody.create(MediaType.parse("application/json"), ""))
)
