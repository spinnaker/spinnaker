package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import com.netflix.spinnaker.kork.exceptions.SystemException

class UnknownBaseImage(os: String, label: BaseLabel) :
  SystemException("Could not identify base image for os $os and label $label")
