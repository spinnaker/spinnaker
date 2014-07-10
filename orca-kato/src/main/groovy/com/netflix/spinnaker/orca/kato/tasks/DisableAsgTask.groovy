package com.netflix.spinnaker.orca.kato.tasks

import groovy.transform.CompileStatic

@CompileStatic
class DisableAsgTask extends AbstractAsgTask {
  String asgAction = "disableAsg"
}
