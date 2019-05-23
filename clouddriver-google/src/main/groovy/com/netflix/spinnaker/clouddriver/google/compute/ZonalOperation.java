package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;

class ZonalOperation implements WaitableComputeOperation {

  private final Operation operation;
  private final GoogleNamedAccountCredentials credentials;
  private final GoogleOperationPoller poller;

  ZonalOperation(
      Operation operation,
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller poller) {
    this.operation = operation;
    this.credentials = credentials;
    this.poller = poller;
  }

  @Override
  public Operation waitForDone(Task task, String phase) {
    return poller.waitForZonalOperation(
        credentials.getCompute(),
        credentials.getProject(),
        GCEUtil.getLocalName(operation.getZone()),
        operation.getName(),
        /* timeoutSeconds= */ null,
        task,
        GCEUtil.getLocalName(operation.getTargetLink()),
        phase);
  }
}
