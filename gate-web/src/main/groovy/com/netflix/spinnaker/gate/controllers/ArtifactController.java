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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/artifacts")
public class ArtifactController {

  @Autowired
  private ArtifactService artifactService;

  @ApiOperation(value = "Retrieve the list of artifact accounts configured in Clouddriver.", response = HashMap.class, responseContainer = "List")
  @RequestMapping(method = RequestMethod.GET, value = "/credentials")
  List<Map> all(@RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    return artifactService.getArtifactCredentials(sourceApp);
  }
}
