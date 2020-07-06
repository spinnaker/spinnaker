package com.netflix.spinnaker.keel.api.ec2

enum class Metric {
  GroupMinSize, GroupMaxSize, GroupDesiredCapacity, GroupInServiceInstances, GroupPendingInstances,
  GroupStandbyInstances, GroupTerminatingInstances, GroupTotalInstances
}
