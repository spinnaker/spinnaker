/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.concourse;

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.concourse.service.ConcourseService;
import com.netflix.spinnaker.igor.config.ConcourseProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.service.ArtifactDecorator;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.Collections;
import java.util.Optional;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class ConcourseBuildMonitorTest {
  private ArtifactDecorator artifactDecorator = mock(ArtifactDecorator.class);
  private ConcourseCache cache = mock(ConcourseCache.class);
  private EchoService echoService = mock(EchoService.class);
  private IgorConfigurationProperties igorConfigurationProperties =
      new IgorConfigurationProperties();
  private ConcourseBuildMonitor monitor;
  private MockWebServer mockConcourse = new MockWebServer();

  @BeforeEach
  void before() throws Exception {
    mockConcourse.enqueue(
        new MockResponse()
            .setBody("{\"version\": \"6.0.0\"}")
            .setHeader("Content-Type", "application/json;charset=utf-8"));

    ConcourseProperties.Host host = new ConcourseProperties.Host();
    host.setName("test");
    host.setUrl(mockConcourse.url("").toString());
    host.setUsername("fake");
    host.setPassword("fake");

    ConcourseProperties props = new ConcourseProperties();
    props.setMasters(Collections.singletonList(host));

    BuildServices buildServices = new BuildServices();
    buildServices.addServices(
        ImmutableMap.of(
            "test",
            new ConcourseService(
                host,
                Optional.of(artifactDecorator),
                new OkHttp3ClientConfiguration(
                    new OkHttpClientConfigurationProperties(),
                    null,
                    HttpLoggingInterceptor.Level.BASIC,
                    null,
                    null,
                    null))));

    this.monitor =
        new ConcourseBuildMonitor(
            igorConfigurationProperties,
            new NoopRegistry(),
            new DynamicConfigService.NoopDynamicConfig(),
            new DiscoveryStatusListener(true),
            Optional.empty(),
            Optional.of(echoService),
            buildServices,
            cache,
            props,
            mock(TaskScheduler.class));
  }

  @Test
  void shouldHandleAnyFailureToTalkToConcourseGracefully() {
    mockConcourse.enqueue(new MockResponse().setResponseCode(400));
    monitor.poll(false);
  }
}
