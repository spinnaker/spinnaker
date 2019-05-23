package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagersAbandonInstancesRequest;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;

class ZoneGoogleServerGroupManagers extends AbstractGoogleServerGroupManagers {

  private final Compute.InstanceGroupManagers managers;
  private final GoogleOperationPoller operationPoller;
  private final String zone;

  ZoneGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry,
      String instanceGroupName,
      String zone) {
    super(credentials, registry, instanceGroupName);
    this.managers = credentials.getCompute().instanceGroupManagers();
    this.operationPoller = operationPoller;
    this.zone = zone;
  }

  @Override
  ComputeRequest<Operation> performAbandonInstances(List<String> instances) throws IOException {

    InstanceGroupManagersAbandonInstancesRequest request =
        new InstanceGroupManagersAbandonInstancesRequest();
    request.setInstances(instances);
    return managers.abandonInstances(getProject(), zone, getInstanceGroupName(), request);
  }

  @Override
  ComputeRequest<Operation> performDelete() throws IOException {
    return managers.delete(getProject(), zone, getInstanceGroupName());
  }

  @Override
  ComputeRequest<InstanceGroupManager> performGet() throws IOException {
    return managers.get(getProject(), zone, getInstanceGroupName());
  }

  @Override
  ComputeRequest<Operation> performUpdate(InstanceGroupManager content) throws IOException {
    return managers.update(getProject(), zone, getInstanceGroupName(), content);
  }

  @Override
  WaitableComputeOperation wrapOperation(Operation operation) {
    return new ZonalOperation(operation, getCredentials(), operationPoller);
  }

  @Override
  String getManagersType() {
    return "instanceGroupManagers";
  }

  @Override
  List<String> getRegionOrZoneTags() {
    return ImmutableList.of(
        GoogleExecutor.getTAG_SCOPE(),
        GoogleExecutor.getSCOPE_ZONAL(),
        GoogleExecutor.getTAG_ZONE(),
        zone);
  }
}
