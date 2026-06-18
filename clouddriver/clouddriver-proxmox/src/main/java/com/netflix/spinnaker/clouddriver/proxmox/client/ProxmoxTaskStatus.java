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
package com.netflix.spinnaker.clouddriver.proxmox.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Represents the status of a Proxmox background task (UPID). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxTaskStatus {
  /** "running" while the task is executing, "stopped" when complete. */
  private String status;

  /** "OK" on success, or an error message string when {@link #status} is "stopped". */
  private String exitstatus;

  private String type;
  private String id;
  private String upid;
  private String node;

  public boolean isFinished() {
    return "stopped".equals(status);
  }

  public boolean isSuccessful() {
    return isFinished() && "OK".equals(exitstatus);
  }
}
