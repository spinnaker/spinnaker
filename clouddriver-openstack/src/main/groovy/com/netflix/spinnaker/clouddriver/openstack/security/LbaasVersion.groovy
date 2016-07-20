package com.netflix.spinnaker.clouddriver.openstack.security

enum LbaasVersion {
  V1, V2

  String value() {
    return toString().toLowerCase()
  }
}
