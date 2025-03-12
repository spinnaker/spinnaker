package com.netflix.spinnaker.keel.api

import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER

/**
 * Indicates that differences between current and desired versions of a property should be
 * ignored. This may be because the property is not relevant to the state but needs to be included
 * in the model for other reasons (for example a server group's name), or because modifying the
 * property after a resource is created is not possible and therefore Keel should avoid flapping
 * trying to actuate an impossible change (for example, a security group description).
 */
@Target(PROPERTY_GETTER) // Ideally this would be PROPERTY but the java-object-diff library looks for method annotations and it's not easy to override that behavior
annotation class ExcludedFromDiff
