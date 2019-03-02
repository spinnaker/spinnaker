package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion

class UnsupportedKind(apiVersion: ApiVersion, kind: String) :
  IllegalStateException("No plugin supporting \"$kind\" in \"$apiVersion\" is available")
