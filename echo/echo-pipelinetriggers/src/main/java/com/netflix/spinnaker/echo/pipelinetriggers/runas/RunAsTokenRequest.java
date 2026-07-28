/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.runas;

/**
 * Request body for Front50's initial run-as token mint endpoint. Mirrors Front50's {@code
 * RunAsTokenRequest} contract: the caller posts the managed service account it wants to run as plus
 * the id of the pipeline being triggered, which Front50 uses to bind the mint to the pipeline's
 * configured {@code runAsUser}.
 */
public class RunAsTokenRequest {

  private String serviceAccount;
  private String pipelineId;

  public RunAsTokenRequest() {}

  public RunAsTokenRequest(String serviceAccount, String pipelineId) {
    this.serviceAccount = serviceAccount;
    this.pipelineId = pipelineId;
  }

  public String getServiceAccount() {
    return serviceAccount;
  }

  public void setServiceAccount(String serviceAccount) {
    this.serviceAccount = serviceAccount;
  }

  public String getPipelineId() {
    return pipelineId;
  }

  public void setPipelineId(String pipelineId) {
    this.pipelineId = pipelineId;
  }
}
