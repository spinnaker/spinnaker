package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.bakery.api.BaseLabel

class UnknownBaseImage(os: String, label: BaseLabel) : RuntimeException("Could not identify base image for os $os and label $label")
