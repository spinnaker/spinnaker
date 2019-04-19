package com.netflix.spinnaker.clouddriver.google.deploy.instancegroups;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.RegionInstanceGroupManagersAbandonInstancesRequest;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;

class RegionGoogleServerGroupManagers extends AbstractGoogleServerGroupManagers {

  private final Compute.RegionInstanceGroupManagers managers;
  private final RegionGoogleServerGroupOperationPoller operationPoller;
  private final String region;

  RegionGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry,
      String instanceGroupName,
      String region) {
    super(credentials, registry, instanceGroupName);
    this.managers = credentials.getCompute().regionInstanceGroupManagers();
    this.operationPoller = new RegionGoogleServerGroupOperationPoller(operationPoller);
    this.region = region;
  }

  @Override
  ComputeRequest<Operation> performAbandonInstances(List<String> instances) throws IOException {

    RegionInstanceGroupManagersAbandonInstancesRequest request =
        new RegionInstanceGroupManagersAbandonInstancesRequest();
    request.setInstances(instances);
    return managers.abandonInstances(getProject(), region, getInstanceGroupName(), request);
  }

  @Override
  ComputeRequest<Operation> performDelete() throws IOException {
    return managers.delete(getProject(), region, getInstanceGroupName());
  }

  @Override
  public GoogleServerGroupOperationPoller getOperationPoller() {
    return operationPoller;
  }

  @Override
  String getManagersType() {
    return "regionInstanceGroupManagers";
  }

  @Override
  List<String> getRegionOrZoneTags() {
    return ImmutableList.of(
        GoogleExecutor.getTAG_SCOPE(),
        GoogleExecutor.getSCOPE_REGIONAL(),
        GoogleExecutor.getTAG_REGION(),
        region);
  }

  private class RegionGoogleServerGroupOperationPoller extends GoogleServerGroupOperationPoller {

    private RegionGoogleServerGroupOperationPoller(GoogleOperationPoller poller) {
      super(poller);
    }

    @Override
    public void waitForOperation(Operation operation, Long timeout, Task task, String phase) {
      getPoller()
          .waitForRegionalOperation(
              getCredentials().getCompute(),
              getProject(),
              region,
              operation.getName(),
              timeout,
              task,
              "regional instance group " + getInstanceGroupName(),
              phase);
    }
  }
}
