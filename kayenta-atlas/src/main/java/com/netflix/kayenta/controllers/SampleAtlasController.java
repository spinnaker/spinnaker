/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.controllers;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * This is a temporary stand-in for the /fetch endpoint that will be exposed by OSS Atlas.
 */
@RestController
@RequestMapping("/api/v1/fetch")
public class SampleAtlasController {

  @Autowired
  ResourceLoader resourceLoader;

  @RequestMapping(method = RequestMethod.GET)
  public String queryMetrics(@RequestParam final String q) throws IOException {
    return getJsonContent("com/netflix/kayenta/controllers/sample-atlas-response.json");
  }

  private String getJsonContent(String jsonFilename) throws IOException {
    try (InputStream jsonInputStream = resourceLoader.getResource("classpath:" + jsonFilename).getInputStream()) {
      return IOUtils.toString(jsonInputStream, Charsets.UTF_8.name());
    }
  }
}
