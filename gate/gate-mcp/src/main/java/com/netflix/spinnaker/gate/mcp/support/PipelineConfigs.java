/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.mcp.support;

import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Resolves a pipeline config by name or id from front50, matching the fallback behavior of {@code
 * ApplicationService.getPipelineConfigForApplication} in gate-web (name lookup first, falling back
 * to id lookup) - reimplemented here directly against {@link Front50Service} because gate-mcp (a
 * dependency of gate-web) cannot depend on gate-web's services.
 */
public final class PipelineConfigs {

  private PipelineConfigs() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> resolve(
      Front50Service front50Service, String application, String pipelineNameOrId) {
    try {
      Map<String, Object> byName =
          (Map<String, Object>)
              Retrofit2SyncCall.execute(
                  front50Service.getPipelineConfigByApplicationAndName(
                      application, pipelineNameOrId, true));
      if (pipelineNameOrId.equals(byName.get("name"))) {
        return byName;
      }
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() != HttpStatus.NOT_FOUND.value()) {
        throw e;
      }
    }

    Map<String, Object> byId =
        (Map<String, Object>)
            Retrofit2SyncCall.execute(front50Service.getPipelineConfigById(pipelineNameOrId));
    if (pipelineNameOrId.equals(byId.get("id"))) {
      return byId;
    }

    throw new NotFoundException(
        "Pipeline configuration not found (nameOrId: "
            + pipelineNameOrId
            + " in application "
            + application
            + ")");
  }
}
