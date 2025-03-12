package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue

/**
 * get the exception - can be either general orca exception or clouddriver-specific
 */
fun List<Map<String, Any>>?.getFailureMessage(mapper: ObjectMapper): String? {

  this?.forEach { it ->
    val context: OrcaContext? = it["context"]?.let { mapper.convertValue(it) }

    // find the first exception and return
    if (context?.exception != null) {
      return context.exception.details?.errors?.joinToString(",")
    }

    if (context?.clouddriverException != null) {
      val clouddriverError: ClouddriverException? = context.clouddriverException.first()["exception"]?.let { mapper.convertValue(it) }
      return clouddriverError?.message
    }
  }

  return null
}
