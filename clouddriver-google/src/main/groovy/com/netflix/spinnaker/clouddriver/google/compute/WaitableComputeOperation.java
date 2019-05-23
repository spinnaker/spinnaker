package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.data.task.Task;

public interface WaitableComputeOperation {

  Operation waitForDone(Task task, String phase);
}
