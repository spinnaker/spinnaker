package com.netflix.spinnaker.keel.retrofit

import org.springframework.http.HttpStatus.NOT_FOUND
import retrofit2.HttpException

/**
 * Is this exception an HTTP 404?
 */
val HttpException.isNotFound: Boolean
  get() = code() == NOT_FOUND.value()
