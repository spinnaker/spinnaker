/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.ArtifactService;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/artifacts")
public class ArtifactController {

  @Autowired
  private ArtifactService artifactService;

  @ApiOperation(value = "Retrieve the list of artifact accounts configured in Clouddriver.", response = List.class)
  @RequestMapping(method = RequestMethod.GET, value = "/credentials")
  List<Map> all(@RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    return artifactService.getArtifactCredentials(sourceApp);
  }

  @ApiOperation(value = "Fetch the contents of an artifact", response = StreamingResponseBody.class)
  @RequestMapping(method = RequestMethod.PUT, value = "/fetch")
  StreamingResponseBody fetch(
    @RequestBody Map<String, String> artifact,
    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp
  ) {
    return new StreamingResponseBody() {
      public void writeTo (OutputStream outputStream) throws IOException {
        artifactService.getArtifactContents(sourceApp, artifact, outputStream);
        outputStream.flush();
      }
    };
  }

  @ApiOperation(value = "Retrieve the list of artifact names that belong to chosen account")
  @RequestMapping(value = "/account/{accountName}/names", method = RequestMethod.GET)
  List<String> artifactNames(
    @PathVariable String accountName,
    @RequestParam String type,
    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp
  ) {
    return artifactService.getArtifactNames(sourceApp, accountName, type);
  }

  @ApiOperation(value = "Retrieve the list of artifact versions by account and artifact names")
  @RequestMapping(value = "/account/{accountName}/versions", method = RequestMethod.GET)
  List<String> artifactVersions(
    @PathVariable String accountName,
    @RequestParam String type,
    @RequestParam String artifactName,
    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp
  ) {
    return artifactService.getArtifactVersions(sourceApp, accountName, type, artifactName);
  }

  @ApiOperation(value = "Retrieve the available artifact versions for an artifact provider and package name")
  @RequestMapping(value = "/{provider}/{packageName}", method = RequestMethod.GET)
  List<String> getVersionsOfArtifactForProvider(
    @PathVariable String provider,
    @PathVariable String packageName
  ) {
    return artifactService.getVersionsOfArtifactForProvider(provider, packageName);
  }
}
