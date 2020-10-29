package com.netflix.spinnaker.keel.api.schema

import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

/**
 * Explicitly marks a constructor parameter as optional in the API schema, overriding whatever
 * heuristic would normally apply.
 */
@Target(VALUE_PARAMETER)
annotation class Optional
