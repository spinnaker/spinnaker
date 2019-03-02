package com.netflix.spinnaker.keel.plugin

/**
 * Represents some kind of conflict that prevents the plugin from interacting with the resource.
 */
class ResourceConflict(message: String) : RuntimeException(message)
