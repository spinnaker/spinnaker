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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.TimeseriesData;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
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

  private ObjectMapper objectMapper = new ObjectMapper();

  private static final Map<String, String> queryMap = createQueryMap();

  private static Map<String, String> createQueryMap() {
    Map<String, String> temp = new HashMap<String, String>();

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

  private AtlasResults generateDummyContent(String q, String s, String e, String step) {
    long stepLong = Duration.parse(step).toMillis();
    long sLong = Long.parseLong(s);
    long eLong = Long.parseLong(e);
    sLong = (sLong / stepLong) * stepLong;
    eLong = (eLong / stepLong) * stepLong;
    long count = (eLong - sLong) / stepLong;

    return AtlasResults.builder()
      .type("timeseries")
      .query(q).start(Long.parseLong(s))
      .end(Long.parseLong(e))
      .step(stepLong)
      .label("dummyLabel")
      .id("dummyId")
      .tags(new HashMap<String, String>())
      .data(TimeseriesData.dummy("array", count))
      .build();
  }

  @RequestMapping(method = RequestMethod.GET, produces = "text/event-stream")
  public String queryMetrics(@RequestParam(defaultValue = "") final String q,
                             @RequestParam(defaultValue = "0") final String s,
                             @RequestParam(defaultValue = "6000000") final String e,
                             @RequestParam(defaultValue = "PT1M") final String step) throws IOException {
    if (queryMap.containsKey(q)) {
      return getFileContent(queryMap.get(q));
    } else {
      String lines[] = {
              "data: " + objectMapper.writeValueAsString(generateDummyContent(q, s, e, step)),
              "data: {\"type\": \"close\"}"
      };
      return String.join("\n\n", lines);
    }
  }

  private String getFileContent(String filename) throws IOException {
    try (InputStream inputStream = resourceLoader.getResource("classpath:" + filename).getInputStream()) {
      return IOUtils.toString(inputStream, Charsets.UTF_8.name());
    }
  }

}
