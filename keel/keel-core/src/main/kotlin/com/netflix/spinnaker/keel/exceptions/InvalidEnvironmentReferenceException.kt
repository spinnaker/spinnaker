package com.netflix.spinnaker.keel.exceptions

class InvalidEnvironmentReferenceException(
  invalidReference: String,
  validReferences: List<String>
) : ValidationException(
  "Base environment ($invalidReference) referenced in preview environment does not exist. " +
    "Valid environments are: ${validReferences.joinToString(", ")}."
)
