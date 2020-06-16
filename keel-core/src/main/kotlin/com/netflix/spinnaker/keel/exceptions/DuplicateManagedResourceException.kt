package com.netflix.spinnaker.keel.exceptions

class DuplicateManagedResourceException(
  val id: String,
  existingConfig: String,
  newConfig: String
) : ValidationException(
  "Resource with id $id exist in a delivery config named ($existingConfig). " +
    "Please ensure to remove resource $id from config named $existingConfig, if you wish this resource to be managed in $newConfig."
)
