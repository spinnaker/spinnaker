package com.netflix.spinnaker.keel.activation

/**
 * Event indicating that the instance changed status from down to up or vice-versa.
 */
sealed class ApplicationStatusChangeEvent

object ApplicationUp : ApplicationStatusChangeEvent()

object ApplicationDown : ApplicationStatusChangeEvent()
