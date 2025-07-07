/*
 * Copyright 2025 Harness, Inc.
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

import com.netflix.spinnaker.gate.services.ChartImageService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/charts")
@RestController
public class ChartImageController {

  @Autowired private ChartImageService chartImageService;

  @Operation(summary = "Get helm chart image details")
  @RequestMapping(value = "/{account}/{region}/{imageId:.+}", method = RequestMethod.GET)
  public List<Map> getImageDetails(
      @PathVariable(value = "account") String account,
      @PathVariable(value = "region") String region,
      @PathVariable(value = "imageId") String imageId,
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    return chartImageService.getForAccountAndRegion(provider, account, region, imageId, sourceApp);
  }

  @Operation(
      summary =
          "Retrieve a list of helm chart images, filtered by cloud provider, region, and account",
      description = "The query parameter `q` filters the list of images by image name")
  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public List<Map> findImages(
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "q", required = false) String query,
      @RequestParam(value = "region", required = false) String region,
      @RequestParam(value = "account", required = false) String account,
      @RequestParam(value = "count", required = false) Integer count,
      HttpServletRequest httpServletRequest) {

    Map<String, String> additionalFilters =
        Collections.list(httpServletRequest.getParameterNames()).stream()
            .filter(parameterName -> !isStandardParameter(parameterName.toLowerCase()))
            .collect(
                Collectors.toMap(
                    parameterName -> parameterName,
                    parameterName -> httpServletRequest.getParameter(parameterName)));

    return chartImageService.search(
        provider,
        query,
        region,
        account,
        count,
        additionalFilters,
        httpServletRequest.getHeader("X-RateLimit-Header"));
  }

  private boolean isStandardParameter(String parameterName) {
    return parameterName.equals("provider")
        || parameterName.equals("q")
        || parameterName.equals("region")
        || parameterName.equals("account")
        || parameterName.equals("count");
  }

  @Operation(summary = "Find tags")
  @RequestMapping(value = "/tags", method = RequestMethod.GET)
  public List<String> findTags(
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "account", required = true) String account,
      @RequestParam(value = "repository", required = true) String repository,
      @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    return chartImageService.findTags(provider, account, repository, sourceApp);
  }
}
