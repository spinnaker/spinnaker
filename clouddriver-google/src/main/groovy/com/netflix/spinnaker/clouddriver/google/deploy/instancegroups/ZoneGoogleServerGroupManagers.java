package com.netflix.spinnaker.clouddriver.google.deploy.instancegroups;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.InstanceGroupManagersAbandonInstancesRequest;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;

class ZoneGoogleServerGroupManagers extends AbstractGoogleServerGroupManagers {

  private final Compute.InstanceGroupManagers managers;
  private final ZoneGoogleServerGroupOperationPoller operationPoller;
  private final String zone;

  ZoneGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry,
      String instanceGroupName,
      String zone) {
    super(credentials, registry, instanceGroupName);
    this.managers = credentials.getCompute().instanceGroupManagers();
    this.operationPoller = new ZoneGoogleServerGroupOperationPoller(operationPoller);
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
  public GoogleServerGroupOperationPoller getOperationPoller() {
    return operationPoller;
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

  private class ZoneGoogleServerGroupOperationPoller extends GoogleServerGroupOperationPoller {

    private ZoneGoogleServerGroupOperationPoller(GoogleOperationPoller poller) {
      super(poller);
    }

    @Override
    public void waitForOperation(Operation operation, Long timeout, Task task, String phase) {
      getPoller()
          .waitForZonalOperation(
              getCredentials().getCompute(),
              getProject(),
              zone,
              operation.getName(),
              timeout,
              task,
              "zonal instance group " + getInstanceGroupName(),
              phase);
    }
  }
}
