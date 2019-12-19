/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import retrofit.RetrofitError;

/** Exposes APIs to retrieve Managed Delivery declarative manifests from source control repos. */
@RestController
public class ManagedDeliveryScmController {
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);

  private final ManagedDeliveryScmService mdScmService;

  public ManagedDeliveryScmController(ManagedDeliveryScmService mdScmService) {
    this.mdScmService = mdScmService;
  }

  /**
   * Given details about a supported git source control repository, and optional filters for a
   * sub-directory within the repo, the file extensions to look for, and the specific git reference
   * to use, returns a list of (potential) Managed Delivery config manifests found at that location.
   *
   * <p>Note that this method does not recurse the specified sub-directory when listing files.
   */
  @GetMapping("/delivery-config/manifests")
  public List<String> listDeliveryConfigManifests(
      @RequestParam final String scmType,
      @RequestParam final String project,
      @RequestParam final String repository,
      @RequestParam(required = false) final String directory,
      @RequestParam(required = false) final String extension,
      @RequestParam(required = false) final String ref) {

    return mdScmService.listDeliveryConfigManifests(
        scmType, project, repository, directory, extension, ref);
  }

  /**
   * Given details about a supported git source control repository, the filename of a Managed
   * Delivery config manifest, and optional filters for a sub-directory within the repo and the
   * specific git reference to use, returns the contents of the manifest.
   *
   * <p>This API supports both YAML and JSON for the format of the manifest in source control, but
   * always returns the contents as JSON.
   */
  @GetMapping(path = "/delivery-config/manifest")
  public ResponseEntity<Map<String, Object>> getDeliveryConfigManifest(
      @RequestParam String scmType,
      @RequestParam final String project,
      @RequestParam final String repository,
      @RequestParam final String manifest,
      @RequestParam(required = false) final String directory,
      @RequestParam(required = false) final String ref) {
    try {
      return new ResponseEntity<>(
          mdScmService.getDeliveryConfigManifest(
              scmType, project, repository, directory, manifest, ref),
          HttpStatus.OK);
    } catch (Exception e) {
      HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
      Object errorDetails = e.getMessage();
      if (e instanceof IllegalArgumentException) {
        status = HttpStatus.BAD_REQUEST;
      } else if (e instanceof RetrofitError) {
        RetrofitError re = (RetrofitError) e;
        if (re.getKind() == RetrofitError.Kind.HTTP
            && re.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
          status = HttpStatus.NOT_FOUND;
          errorDetails = re.getBodyAs(Map.class);
        } else {
          errorDetails = "Error calling downstream system: " + re.getMessage();
        }
      }
      return buildErrorResponse(status, errorDetails);
    }
  }

  private ResponseEntity<Map<String, Object>> buildErrorResponse(
      HttpStatus status, Object errorDetails) {
    Map<String, Object> error = Collections.singletonMap("error", errorDetails);
    return new ResponseEntity<>(error, status);
  }
}
