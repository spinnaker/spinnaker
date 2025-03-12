package com.netflix.spinnaker.keel.api.schema

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.PROPERTY

/**
 * Describes a class or property for the API schema.
 */
@Target(CLASS, PROPERTY)
annotation class Description(val value: String)
