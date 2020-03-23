package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

class ExportError(message: String) : SystemException(message)
