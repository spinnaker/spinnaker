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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a temporary stand-in for the /fetch endpoint that will be exposed by OSS Atlas.
 */
@RestController
@RequestMapping("/api/v2/fetch")
public class SampleAtlasController {

  @Autowired
  private ResourceLoader resourceLoader;

  private static final Map<String, String> queryMap = createQueryMap();

  private static Map<String, String> createQueryMap()
  {
    Map<String,String> temp = new HashMap<String,String>();

    temp.put("nf.app,app_mocha,:eq,name,CpuRawSystem,:eq,:and,(,nf.cluster,),:by",
             "com/netflix/kayenta/controllers/sample00.sse");

    temp.put("nf.app,app_wesley,:eq,name,CpuRawSystem,:eq,:and",
            "com/netflix/kayenta/controllers/sample01.sse");

    temp.put("nf.app,app_leo,:eq,name,CpuRawSystem,:eq,:and,(,nf.node,),:by",
            "com/netflix/kayenta/controllers/sample02.sse");

    temp.put("nf.app,app_nyassa,:eq,name,CpuRawSystem,:eq,:and,(,nf.node,),:by",
            "com/netflix/kayenta/controllers/sample03.sse");

    return temp;
  }

  @RequestMapping(method = RequestMethod.GET, produces = "text/event-stream")
  public String queryMetrics(@RequestParam final String q) throws IOException {
    return getSSEContent(queryMap.getOrDefault(q, "com/netflix/kayenta/controllers/empty.sse"));
  }

  private String getSSEContent(String sseFilename) throws IOException {
    try (InputStream jsonInputStream = resourceLoader.getResource("classpath:" + sseFilename).getInputStream()) {
      return IOUtils.toString(jsonInputStream, Charsets.UTF_8.name());
    }
  }
}
