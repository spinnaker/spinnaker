/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.WorkRequest
import com.oracle.bmc.loadbalancer.requests.GetWorkRequestRequest

public class OracleWorkRequestPoller {
  
  static OracleWorkRequestPoller poller = new OracleWorkRequestPoller();
  
  public WorkRequest wait(String workRequestId, String phase, Task task, LoadBalancerClient loadBalancerClient) {
    def wr = GetWorkRequestRequest.builder().workRequestId(workRequestId).build()

    task.updateStatus(phase, "Waiting for WorkRequest to finish: $workRequestId")
    def waiter = loadBalancerClient.waiters.forWorkRequest(wr)
    def finalWorkRequestResult = waiter.execute().workRequest

    if (finalWorkRequestResult.lifecycleState != WorkRequest.LifecycleState.Succeeded) {
      task.updateStatus(phase, "WorkRequest finished: ${finalWorkRequestResult.lifecycleState} ${finalWorkRequestResult.message}")
      for (def err : finalWorkRequestResult.errorDetails) {
        task.updateStatus(phase, "Error Code: ${err.errorCode}, Message: ${err.message}")
      }
      task.fail()
    } else {
      task.updateStatus(phase, "WorkRequest finished: ${finalWorkRequestResult.lifecycleState}")
    }
    return finalWorkRequestResult
  }

  public static WorkRequest poll(String workRequestId, String phase, Task task, LoadBalancerClient loadBalancerClient) {
    return poller.wait(workRequestId, phase, task, loadBalancerClient);
  }
}
