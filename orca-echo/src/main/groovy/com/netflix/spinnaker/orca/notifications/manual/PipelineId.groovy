package com.netflix.spinnaker.orca.notifications.manual

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@Immutable
@CompileStatic
class PipelineId implements Serializable {
  String application
  String name
}
