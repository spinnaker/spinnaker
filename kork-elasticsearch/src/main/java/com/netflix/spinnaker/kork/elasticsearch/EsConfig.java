/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.elasticsearch;

import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.annotation.PreDestroy;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * Configuration for embedded es instance and associated jedis
 */
@ConditionalOnMissingClass(name = "com.netflix.spinnaker.platform.netflix.elasticsearch.NetflixPlatformElasticSearch")
public class EsConfig {

  private static final Logger log = LoggerFactory.getLogger(EsConfig.class);

  @Autowired
  Environment environment;
  Node node;

  @Bean
  public Client es(@Value("${elasticsearch.cluster:none}") String cluster
  ) {
    if (cluster.equals("none")) {
      node = nodeBuilder().local(true).node();
    } else {
      node = nodeBuilder().clusterName(cluster).node(); // loads from a local elasticsearch.yml definition
    }
    return node.client();
  }

  @PreDestroy
  void destroy() {
    if (node != null) {
      log.info("stopping es server");
      try {
        node.close();
      } catch (Exception e) {
        log.error("could not stop es server", e);
      }
    }
  }

}
