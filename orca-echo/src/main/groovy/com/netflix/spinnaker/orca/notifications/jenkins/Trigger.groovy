package com.netflix.spinnaker.orca.notifications.jenkins

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@Immutable
@CompileStatic
class Trigger implements Serializable {
  String master
  String job
}
