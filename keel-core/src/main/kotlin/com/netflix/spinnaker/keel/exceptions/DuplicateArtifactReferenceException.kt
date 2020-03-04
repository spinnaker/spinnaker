package com.netflix.spinnaker.keel.exceptions

class DuplicateArtifactReferenceException(
  private val artifactNameToRef: Map<String, String>
) : ValidationException(
  "Multiple artifacts are using the same reference: $artifactNameToRef. " +
    "Please ensure each artifact has a unique reference."
)
