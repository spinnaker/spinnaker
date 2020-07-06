package com.netflix.spinnaker.keel.api.docs

import kotlin.annotation.AnnotationTarget.CLASS

@Target(CLASS)
annotation class Literal(val type: String = "string", val value: String)
