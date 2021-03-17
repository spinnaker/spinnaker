package com.netflix.spinnaker.orca

/**
 * Default implementation just uses the task definition as is and will not to do any changes to the
 * task definition itself.
 */
class NoOpTaskImplementationResolver: TaskImplementationResolver
