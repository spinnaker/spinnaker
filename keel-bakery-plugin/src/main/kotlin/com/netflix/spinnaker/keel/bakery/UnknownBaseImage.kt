package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import com.netflix.spinnaker.kork.exceptions.UserException

class UnknownBaseImage(os: String, label: BaseLabel) : UserException("Could not identify base image for os $os and label $label")
