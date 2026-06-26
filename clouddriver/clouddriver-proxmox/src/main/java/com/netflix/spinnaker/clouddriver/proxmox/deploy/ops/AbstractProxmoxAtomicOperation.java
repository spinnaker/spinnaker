/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.proxmox.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxResponse;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxTaskStatus;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@Slf4j
public abstract class AbstractProxmoxAtomicOperation<R> implements AtomicOperation<R> {

  private static final int POLL_INTERVAL_MS = 3_000;
  private static final int MAX_POLLS = 200; // 10 minutes

  protected final String phase;

  protected AbstractProxmoxAtomicOperation(String phase) {
    this.phase = phase;
  }

  protected Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  protected void updateStatus(String message) {
    getTask().updateStatus(phase, message);
  }

  /**
   * Blocks until the Proxmox task identified by {@code upid} completes successfully, updating task
   * status at each poll interval.
   *
   * @throws RuntimeException if the task fails or times out
   */
  protected void pollTaskUntilDone(ProxmoxApiService api, String node, String upid) {
    updateStatus("Waiting for Proxmox task " + upid);
    for (int i = 0; i < MAX_POLLS; i++) {
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for Proxmox task " + upid, e);
      }
      ProxmoxTaskStatus status = executeCall(api.getTaskStatus(node, upid));
      if (status.isFinished()) {
        if (!status.isSuccessful()) {
          throw new RuntimeException("Proxmox task " + upid + " failed: " + status.getExitstatus());
        }
        updateStatus("Task " + upid + " completed successfully.");
        return;
      }
    }
    throw new RuntimeException(
        "Timed out after " + (MAX_POLLS * POLL_INTERVAL_MS / 1000) + "s waiting for " + upid);
  }

  /**
   * Like {@link #pollTaskUntilDone} but swallows failures — useful when a best-effort preceding
   * step (e.g. stop-before-delete) may fail because the resource is already in the target state.
   */
  protected void pollTaskIgnoringFailure(ProxmoxApiService api, String node, String upid) {
    try {
      pollTaskUntilDone(api, node, upid);
    } catch (RuntimeException e) {
      log.warn("Ignoring task failure for {}: {}", upid, e.getMessage());
    }
  }

  protected static <T> T executeCall(Call<ProxmoxResponse<T>> call) {
    try {
      retrofit2.Response<ProxmoxResponse<T>> response = call.execute();
      if (!response.isSuccessful()) {
        String errorBody = null;
        try {
          if (response.errorBody() != null) errorBody = response.errorBody().string();
        } catch (IOException ignored) {
        }
        throw new RuntimeException(
            "Proxmox API error: HTTP "
                + response.code()
                + " "
                + response.message()
                + (errorBody != null ? " — " + errorBody : ""));
      }
      ProxmoxResponse<T> body = response.body();
      return body != null ? body.getData() : null;
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute Proxmox API call", e);
    }
  }
}
