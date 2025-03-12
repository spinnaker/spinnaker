package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.UserException

class NoSuchEnvironmentException(env: String, application: String) : UserException("No environment $env exists in config for $application")
