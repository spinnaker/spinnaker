/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mayo.services
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.mayo.MayoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class PipelineConfigurationServiceImpl implements PipelineConfigurationService {

  private List<Map> pipelines = []

  @Autowired
  MayoService mayoService

  @Autowired
  ObjectMapper objectMapper

  List<Map> getPipelines() {
    def list = []
    for (pipeline in pipelines) {
      list << new HashMap(pipeline)
    }
    ImmutableList.copyOf(list)
  }

  @PostConstruct
  void init() {
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new PipelinePoller(), 0, 15, TimeUnit.SECONDS)
  }

  private class PipelinePoller implements Runnable {
    void run() {
      try {
        pipelines = objectMapper.readValue(mayoService.pipelines.body.in().text, new TypeReference<List<Map>>() {})
      } catch (e) {
        e.printStackTrace()
      }
    }
  }
}
