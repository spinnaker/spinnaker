package com.netflix.spinnaker.clouddriver.azure.resources.instance.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable

class AzureInstanceDescription extends AzureResourceOpsDescription implements ServerGroupsNameable {
  List<String> instanceIds
  // terminateInstanceAndDecrementServerGroup sends a single 'instance' rather than
  // 'instanceIds'; see orca's TerminatingInstanceSupport.
  String instance
  String serverGroupName
  String asgName

  List<String> getTargetInstanceIds() {
    instanceIds ?: (instance ? [instance] : [])
  }

  String getTargetServerGroupName() {
    serverGroupName ?: asgName
  }

  @Override
  Collection<String> getServerGroupNames() {
    getTargetServerGroupName() ? [getTargetServerGroupName()] : []
  }
}
