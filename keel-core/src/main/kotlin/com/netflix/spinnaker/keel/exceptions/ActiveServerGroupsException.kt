package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

class ActiveServerGroupsException(
  val resourceId: String,
  val error: String
) : SystemException("There were too many active server groups, " +
  "and an error occurred when trying to identify which server group to disable, for resource $resourceId: $error")
