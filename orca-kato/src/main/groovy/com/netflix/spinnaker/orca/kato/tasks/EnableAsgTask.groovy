package com.netflix.spinnaker.orca.kato.tasks

import groovy.transform.CompileStatic

@CompileStatic
class EnableAsgTask extends AbstractAsgTask {
  String asgAction = "enableAsg"
}
