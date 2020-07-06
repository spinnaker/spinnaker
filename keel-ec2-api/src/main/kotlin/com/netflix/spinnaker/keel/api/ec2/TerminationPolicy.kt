package com.netflix.spinnaker.keel.api.ec2

enum class TerminationPolicy {
  Default, OldestInstance, NewestInstance, OldestLaunchConfiguration, ClosestToNextInstanceHour
}
