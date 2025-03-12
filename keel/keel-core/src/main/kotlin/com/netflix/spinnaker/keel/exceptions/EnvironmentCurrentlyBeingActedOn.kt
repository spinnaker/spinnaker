package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * Exception thrown when it's not safe to take action against the environment because
 * something is already acting on it.
 */
open class EnvironmentCurrentlyBeingActedOn(message: String) : SystemException(message)

