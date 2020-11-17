package com.netflix.spinnaker.keel.spring.test

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.TYPE

/**
 * Disables `@Scheduled` tasks. This is useful in Spring tests where you don't want stuff running in
 * the background.
 */
@Target(CLASS, TYPE)
@Retention(RUNTIME)
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
annotation class DisableSpringScheduling
