/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.CiService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ci")
public class CiController {

  private CiService ciService;

  @Autowired
  public CiController(CiService ciService) {
    this.ciService = ciService;
  }

  @RequestMapping(value = "/builds", method = RequestMethod.GET)
  List getBuilds(
      @RequestParam(value = "projectKey") String projectKey,
      @RequestParam(value = "repoSlug") String repoSlug,
      @RequestParam(value = "completionStatus", required = false) String completionStatus) {
    return ciService.getBuilds(projectKey, repoSlug, completionStatus);
  }
}
