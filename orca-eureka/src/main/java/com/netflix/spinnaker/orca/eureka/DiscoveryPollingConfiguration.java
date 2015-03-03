/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.eureka;

import com.netflix.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import rx.schedulers.Schedulers;

@Configuration
public class DiscoveryPollingConfiguration {

  @Autowired(required = false)
  DiscoveryClient discoveryClient;

  @Autowired ApplicationContext context;

  @Bean
  public ApplicationListener<ContextRefreshedEvent> discoveryStatusPoller() {
    if (discoveryClient == null) {
      return new NoDiscoveryApplicationStatusPublisher(context);
    } else {
      return new DiscoveryStatusPoller(
        discoveryClient,
        30,
        Schedulers.io(),
        context
      );
    }
  }
}
