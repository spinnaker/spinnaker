package com.netflix.spinnaker.keel.api.docs

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.PROPERTY

@Target(CLASS, PROPERTY)
annotation class Description(val value: String)
