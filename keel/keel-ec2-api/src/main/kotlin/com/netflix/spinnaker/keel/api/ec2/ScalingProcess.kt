package com.netflix.spinnaker.keel.api.ec2

enum class ScalingProcess {
  Launch,
  Terminate,
  AddToLoadBalancer,
  AlarmNotification,
  AZRebalance,
  HealthCheck,
  ReplaceUnhealthy,
  ScheduledActions
}
