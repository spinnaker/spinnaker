package com.netflix.spinnaker.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

class LifecycleEventException(
  val artifactRef: String,
  val version: String,
  val detail: String
) : SystemException(
  "There was a problem generating lifecycle steps for artifact ref $artifactRef and version $version. $detail"
)
