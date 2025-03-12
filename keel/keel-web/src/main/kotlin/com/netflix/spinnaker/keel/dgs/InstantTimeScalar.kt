package com.netflix.spinnaker.keel.dgs

import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.Coercing
import com.netflix.graphql.dgs.DgsScalar
import graphql.language.StringValue
import java.time.Instant
import java.time.format.DateTimeParseException


@DgsScalar(name = "InstantTime")
class InstantTimeScalar : Coercing<Instant, String> {
  @Throws(CoercingSerializeException::class)
  override fun serialize(dataFetcherResult: Any): String {
    return if (dataFetcherResult is Instant) {
      dataFetcherResult.toString()
    } else {
      throw CoercingSerializeException("Not a valid DateTime")
    }
  }

  @Throws(CoercingParseValueException::class)
  override fun parseValue(input: Any): Instant {
    return when (input) {
      is Instant -> input
      is String -> {
        try {
          Instant.parse(input)
        } catch (e: DateTimeParseException) {
          throw CoercingParseValueException("Failed to parse input time")
        }
      }
      else -> {
        throw CoercingParseValueException("Input type is invalid")
      }
    }
  }

  @Throws(CoercingParseLiteralException::class)
  override fun parseLiteral(input: Any): Instant {
    if (input is StringValue) {
      try {
        return Instant.parse(input.value)
      } catch (e: DateTimeParseException) {
        throw CoercingParseLiteralException("Failed to parse input time")
      }
    }
    throw CoercingParseLiteralException("Time input is invalid")
  }
}
