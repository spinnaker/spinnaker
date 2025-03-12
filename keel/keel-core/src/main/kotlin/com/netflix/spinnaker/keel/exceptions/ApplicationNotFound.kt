package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

class ApplicationNotFound(name: String) : SystemException("Application $name not found")