package com.netflix.spinnaker.clouddriver.google.deploy.instancegroups;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoogleServerGroupManagersFactory {

  private final GoogleOperationPoller operationPoller;
  private final Registry registry;

  @Autowired
  public GoogleServerGroupManagersFactory(
      GoogleOperationPoller operationPoller, Registry registry) {
    this.operationPoller = operationPoller;
    this.registry = registry;
  }

  public GoogleServerGroupManagers getManagers(
      GoogleNamedAccountCredentials credentials, GoogleServerGroup.View serverGroup) {
    return serverGroup.getRegional()
        ? new RegionGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getRegion())
        : new ZoneGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getZone());
  }
}
