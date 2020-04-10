package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.UnsupportedKind
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.clouddriver.ResourceNotFound
import com.netflix.spinnaker.keel.exceptions.FailedNormalizationException
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.exceptions.ValidationException
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import java.lang.IllegalArgumentException
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ExceptionHandler(
  private val resourceHandlers: List<ResourceHandler<*, *>>
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @ExceptionHandler(HttpMessageConversionException::class, HttpMessageNotReadableException::class)
  @ResponseStatus(BAD_REQUEST)
  fun onParseFailure(e: Exception): ApiError {
    log.error(e.message)
    return when (e.cause) {
      null -> ApiError(e)
      is JsonMappingException ->
        ApiError(e, (e.cause as JsonMappingException).toDetails())
      else -> ApiError(e)
    }
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

  @ExceptionHandler(ValidationException::class)
  @ResponseStatus(BAD_REQUEST)
  fun onInvalidDeliveryConfig(e: ValidationException): ApiError {
    log.error(e.message)
    return ApiError(e)
  }

  @ExceptionHandler(AccessDeniedException::class)
  @ResponseStatus(FORBIDDEN)
  fun onAccessDenied(e: AccessDeniedException): ApiError {
    log.error(e.message)
    return ApiError(
      if (e.message == null || e.message == "Access is denied") {
        "Access denied. Please make sure you have access to the service account specified in your delivery config. " +
          "If you do have access, check that the service account has access to this application along with all the cloud accounts included in the delivery config."
      } else {
        e.message!!
      }
    )
  }

  private fun JsonMappingException.toDetails(): ParsingErrorDetails {
    val (rootCause, problemPath) = findRootCause()

    return ParsingErrorDetails(
      error = when (this) {
        // caters to missing properties at the root-level (e.g. serviceAccount)
        is MissingKotlinParameterException -> ParsingError.MISSING_PROPERTY
        else -> when (rootCause) {
          is MissingKotlinParameterException -> ParsingError.MISSING_PROPERTY
          is IllegalStateException -> ParsingError.INVALID_VALUE
          is IllegalArgumentException -> ParsingError.INVALID_VALUE
          is MismatchedInputException -> ParsingError.INVALID_TYPE
          is InvalidTypeIdException -> ParsingError.INVALID_TYPE
          is InvalidFormatException -> ParsingError.INVALID_FORMAT
          is DateTimeParseException -> ParsingError.INVALID_FORMAT
          else -> ParsingError.OTHER
        }
      },
      message = rootCause.message,
      location = mapOf(
        "line" to (location?.lineNr ?: -1),
        "column" to (location?.columnNr ?: -1)
      ),
      path = problemPath.map { ref ->
        val type = if (ref.from is Class<*>) {
          ref.from as Class<*>
        } else {
          ref.from.javaClass
        }
        mapOf(
          "type" to if (Set::class.java.isAssignableFrom(type)) {
            // for some reason, Jackson says the type of arrays in the JSON is a java.util.HashSet
            "array"
          } else if (ResourceSpec::class.java.isAssignableFrom(type)) {
            // for ResourceSpec sub-types, use the API version/kind instead of the class name
            val handler = resourceHandlers.supporting(type as Class<ResourceSpec>)
            if (handler != null) {
              handler.supportedKind.kind
            } else {
              type.name
            }
          } else {
            type.name
          },
          "field" to ref.fieldName,
          "index" to if (ref.index == -1) {
            null
          } else {
            ref.index
          }
        )
      }
    )
  }

  private fun JsonMappingException.findRootCause(): Pair<Throwable, List<JsonMappingException.Reference>> {
    if (this.cause == null) {
      return Pair(this, this.path)
    }
    var exception: Throwable = this
    var paths: MutableList<JsonMappingException.Reference> = this.path.toMutableList()
    while (exception.cause != null && exception.cause != exception) {
      exception = exception.cause!!
      if (exception is JsonMappingException) {
        paths.addAll(exception.path)
      }
    }
    return Pair(exception, paths)
  }
}

/**
 * Error details returned as JSON/YAML.
 */
data class ApiError(
  val message: String?,
  val details: ApiErrorDetails?
) {
  constructor(ex: Throwable, details: ApiErrorDetails? = null) :
    this(ex.cause?.message ?: ex.message, details)
  constructor(message: String) :
    this(message, null)
}

interface ApiErrorType

enum class ParsingError : ApiErrorType {
  MISSING_PROPERTY,
  INVALID_TYPE,
  INVALID_FORMAT,
  INVALID_VALUE, // used when we can't determine if the problem is type or format
  OTHER;

  @JsonValue
  fun toLowerCase() = name.toLowerCase()
}

interface ApiErrorDetails {
  val type: ApiErrorType
}

data class ParsingErrorDetails(
  val error: ParsingError,
  val message: String?,
  val location: Map<String, Int>,
  val path: List<Map<String, Any?>>
) : ApiErrorDetails {
  override val type: ApiErrorType = error
  val pathExpression: String
  init {
    // Makes a JSONPath expression for the problem path
    pathExpression = "".let { str ->
      var sum = str
      path.forEach {
        sum += when {
          it["field"] != null -> "." + it["field"]
          it["type"] == "array" -> "[" + it["index"].toString() + "]"
          else -> ""
        }
      }
      sum
    }
  }
}
