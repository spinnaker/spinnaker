package com.netflix.spinnaker.keel.api.schema

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Specifies that the class is represented in the API schema as a literal value. This would
 * typically be useful on a Kotlin `object`.
 */
@Target(CLASS)
annotation class Literal(val type: String = "string", val value: String)
