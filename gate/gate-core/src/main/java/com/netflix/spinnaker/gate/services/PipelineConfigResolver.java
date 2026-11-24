/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * Resolves a pipeline config by name or id from front50 for a particular application. Shared by
 * gate-web's {@code ApplicationService} and the MCP server so both apply the same rules.
 */
@Slf4j
public final class PipelineConfigResolver {

  private PipelineConfigResolver() {}

  /**
   * Return the pipeline configuration from front50.
   *
   * @param app the application of the pipeline
   * @param pipelineNameOrId the name or id of the pipeline
   * @return the pipeline configuration; throws if not found
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> resolve(
      Front50Service front50Service, String app, String pipelineNameOrId) {
    // Since the argument can be a pipeline name or id, handle both cases.
    // Query by name first since that's more likely.
    try {
      Map<String, Object> pipelineConfig =
          (Map<String, Object>)
              Retrofit2SyncCall.execute(
                  front50Service.getPipelineConfigByApplicationAndName(
                      app, pipelineNameOrId, true));
      if (pipelineNameOrId.equals(pipelineConfig.get("name"))) {
        log.debug(
            "front50 returned a pipeline with name {} in application {}", pipelineNameOrId, app);
        return pipelineConfig;
      }
      log.error(
          "front50 query for a pipeline with name {} in application {} returned a pipeline named {}",
          pipelineNameOrId,
          app,
          pipelineConfig.get("name"));
      // Tempting to return null here, but querying by id might work, so give it a shot.
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
        log.info(
            "front50 returned no pipeline with name {} in application {}", pipelineNameOrId, app);
        // fall through to try querying by id
      } else {
        throw e;
      }
    }

    // query by id
    try {
      Map<String, Object> pipelineConfig =
          (Map<String, Object>)
              Retrofit2SyncCall.execute(front50Service.getPipelineConfigById(pipelineNameOrId));
      if (pipelineNameOrId.equals(pipelineConfig.get("id"))) {
        // Pipeline ids are global in front50, but the caller asked for a pipeline in a particular
        // application.  Without this check, an id that happens to equal a name in the requested
        // application (e.g. a pipeline someone saved with the id "dev") would match a pipeline from
        // a different application.
        if (!app.equalsIgnoreCase(String.valueOf(pipelineConfig.get("application")))) {
          log.warn(
              "front50 returned a pipeline with id {} in application {}, not the requested application {}",
              pipelineNameOrId,
              pipelineConfig.get("application"),
              app);
          throw notFound(pipelineNameOrId, app);
        }
        log.debug("front50 returned a pipeline with id {}", pipelineNameOrId);
        return pipelineConfig;
      }
      log.error(
          "front50 query for a pipeline with id {} returned a pipeline with id {}",
          pipelineNameOrId,
          pipelineConfig.get("id"));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == HttpStatus.NOT_FOUND.value()) {
        log.info("front50 returned no pipeline with id {}", pipelineNameOrId);
        throw e.newInstance(
            "Pipeline configuration not found (nameOrId: "
                + pipelineNameOrId
                + " in application "
                + app
                + "): "
                + e.getMessage());
      }
      throw e;
    }

    // If we get here, the query by id returned a pipeline whose id didn't match
    // what we asked for.
    throw notFound(pipelineNameOrId, app);
  }

  private static NotFoundException notFound(String pipelineNameOrId, String app) {
    return new NotFoundException(
        "Pipeline configuration not found (nameOrId: "
            + pipelineNameOrId
            + " in application "
            + app
            + ")");
  }
}
