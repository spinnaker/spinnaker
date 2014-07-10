package com.netflix.spinnaker.orca.kato.api

import groovy.transform.CompileStatic

@CompileStatic
class EnableOrDisableAsgOperation extends Operation {
  String asgName
  List<String> regions
  String credentials
}
