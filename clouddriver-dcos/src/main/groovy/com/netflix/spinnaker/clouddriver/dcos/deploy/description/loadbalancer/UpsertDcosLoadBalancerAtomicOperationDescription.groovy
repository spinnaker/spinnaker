package com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class UpsertDcosLoadBalancerAtomicOperationDescription extends AbstractDcosCredentialsDescription {
  String name
  String region
  String app
  String stack
  String detail
  boolean bindHttpHttps
  double cpus
  int instances
  double mem
  List<String> acceptedResourceRoles

  PortRange portRange

  static class PortRange {
    String protocol
    int minPort
    int maxPort
  }
}
