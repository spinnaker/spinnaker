package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.clouddriver.ResourceNotFound
import com.netflix.spinnaker.keel.exceptions.FailedNormalizationException
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.plugin.UnsupportedKind
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ExceptionHandler {

  @ExceptionHandler(HttpMessageConversionException::class, HttpMessageNotReadableException::class)
  @ResponseStatus(BAD_REQUEST)
  fun onParseFailure(e: Exception): ApiError {
    log.error(e.message)
    return ApiError(e)
  }

  @ExceptionHandler(FailedNormalizationException::class, UnsupportedKind::class)
  @ResponseStatus(UNPROCESSABLE_ENTITY)
  fun onInvalidModel(e: Exception): ApiError {
    log.error(e.message)
    return ApiError(e)
  }

  @ExceptionHandler(NoSuchArtifactException::class, ResourceNotFound::class, NoSuchResourceException::class, InvalidConstraintException::class, NoSuchDeliveryConfigException::class)
  @ResponseStatus(NOT_FOUND)
  fun onNotFound(e: Exception): ApiError {
    log.error(e.message)
    return ApiError(e)
  }

  @ExceptionHandler(ArtifactAlreadyRegistered::class)
  @ResponseStatus(CONFLICT)
  fun onAlreadyRegistered(e: ArtifactAlreadyRegistered): ApiError {
    log.error(e.message)
    return ApiError(e)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

/**
 * Error details returned as JSON/YAML.
 */
data class ApiError(val message: String?) {
  constructor(ex: Throwable) : this(ex.cause?.message ?: ex.message)
}
