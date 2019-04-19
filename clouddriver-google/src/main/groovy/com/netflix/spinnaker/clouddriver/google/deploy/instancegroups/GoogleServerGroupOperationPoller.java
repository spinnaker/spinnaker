package com.netflix.spinnaker.clouddriver.google.deploy.instancegroups;

import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;

public abstract class GoogleServerGroupOperationPoller {

  private final GoogleOperationPoller poller;

  GoogleServerGroupOperationPoller(GoogleOperationPoller poller) {
    this.poller = poller;
  }

  public abstract void waitForOperation(Operation operation, Long timeout, Task task, String phase);

  GoogleOperationPoller getPoller() {
    return poller;
  }
}
