package com.netflix.spinnaker.keel.caffeine

import com.netflix.spinnaker.kork.exceptions.IntegrationException

class CacheLoadingException(message: String, cause: Throwable) : IntegrationException(message, cause)
