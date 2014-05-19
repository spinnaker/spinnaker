package com.netflix.bluespar.orca.kato.api

import com.google.common.base.Optional
import groovy.transform.CompileStatic

@CompileStatic
class DeployDescription extends Operation {
    String application
    String amiName
    Optional<String> stack
    String instanceType
    List<String> securityGroups
    Optional<String> subnetType
    Map<String, List<String>> availabilityZones
    ASGCapacity capacity
    String credentials
}

