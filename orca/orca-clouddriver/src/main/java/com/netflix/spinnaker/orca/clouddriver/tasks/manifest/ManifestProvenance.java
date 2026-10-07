/*
 * Copyright 2026 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Map;

/**
 * Helpers for adding provenance metadata (deployed-by user and pipeline execution id) to Kubernetes
 * operation maps. These values flow to clouddriver and are written as annotations on deployed
 * resources when the {@code kubernetes.v2.apply-provenance-annotations} flag is enabled.
 */
public final class ManifestProvenance {
  static final String DEPLOYED_BY_KEY = "provenance.deployedBy";
  static final String EXECUTION_ID_KEY = "provenance.executionId";

  private ManifestProvenance() {}

  public static void addProvenance(Map<String, Object> operation, StageExecution stage) {
    String user =
        AuthenticatedRequest.getSpinnakerUser()
            .orElseGet(
                () ->
                    stage.getExecution().getAuthentication() != null
                        ? stage.getExecution().getAuthentication().getUser()
                        : null);
    if (user != null) {
      operation.put(DEPLOYED_BY_KEY, user);
    }
    operation.put(EXECUTION_ID_KEY, stage.getExecution().getId());
  }
}
