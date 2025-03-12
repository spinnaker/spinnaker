package com.netflix.spinnaker.keel.exceptions

class InvalidArtifactReferenceException(
  invalidReference: String,
  validReferences: List<String>
) : ValidationException(
  "Config uses an artifact reference ($invalidReference) that does not correspond to any artifacts. Valid references are: $validReferences."
)
