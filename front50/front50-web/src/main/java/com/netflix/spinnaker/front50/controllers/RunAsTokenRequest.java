/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.front50.controllers;

/**
 * Request body for the initial run-as token mint endpoint. An automated/event trigger path (Echo)
 * posts the managed service account it wants to run as plus the id of the pipeline being triggered;
 * Front50 verifies the caller's service credential, confirms the service account is the one the
 * saved pipeline is configured to run as, resolves that account's roles from its own store, and
 * returns a short-lived signed identity token.
 */
public class RunAsTokenRequest {
  /** The managed service account to mint a run-as token for. */
  private String serviceAccount;

  /**
   * The id of the pipeline being triggered. Front50 binds the mint to the saved pipeline's
   * configured {@code runAsUser}/service account so a credential cannot be used to mint an
   * arbitrary subject.
   */
  private String pipelineId;

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
