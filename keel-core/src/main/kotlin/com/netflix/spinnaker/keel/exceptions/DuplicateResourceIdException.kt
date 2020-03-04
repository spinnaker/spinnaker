package com.netflix.spinnaker.keel.exceptions

class DuplicateResourceIdException(
  val ids: List<String>,
  val envsToResources: Map<String, List<String>>
) : ValidationException(
  "Resource(s) with ids $ids exist in more than one environment ($envsToResources). " +
    "Please ensure each resource has a unique id."
)
