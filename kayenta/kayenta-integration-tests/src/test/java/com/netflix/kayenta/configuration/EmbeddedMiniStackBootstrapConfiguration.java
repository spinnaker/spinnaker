/*
 * Copyright 2026 Red Bull GmbH
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
package com.netflix.kayenta.configuration;

import com.netflix.kayenta.utils.EnvironmentUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.ministack.testcontainers.MiniStackContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Starts the S3 object store backing {@code kayenta.aws.accounts} during the bootstrap phase, so
 * the endpoint and credentials are resolvable by the time the AWS account configuration is bound.
 *
 * <p>Replaces Playtika's {@code embedded-minio} module: MinIO's community edition stopped
 * publishing images, and MiniStack covers the same S3 surface while being the emulator the rest of
 * the repository's AWS integration tests already use.
 */
@Slf4j
@Configuration
public class EmbeddedMiniStackBootstrapConfiguration {

  /**
   * Pinned deliberately: {@code MiniStackContainer}'s no-arg constructor resolves {@code latest},
   * and the emulator releases weekly, so the tag is the only thing that fixes the version these
   * tests run against.
   */
  private static final String MINISTACK_IMAGE_TAG = "1.5.10";

  @Bean(name = "ministack", destroyMethod = "stop")
  public MiniStackContainer ministack(ConfigurableEnvironment environment) {
    MiniStackContainer container = new MiniStackContainer(MINISTACK_IMAGE_TAG);
    container.start();
    Map<String, Object> env = registerEnvironment(environment, container);
    log.info("Started MiniStack. Connection details: {}", env);
    return container;
  }

  static Map<String, Object> registerEnvironment(
      ConfigurableEnvironment environment, MiniStackContainer container) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("embedded.ministack.endpoint", container.getEndpoint());
    map.put("embedded.ministack.region", container.getRegion());
    map.put("embedded.ministack.accessKey", container.getAccessKey());
    map.put("embedded.ministack.secretKey", container.getSecretKey());
    EnvironmentUtils.registerPropertySource("embeddedMiniStackInfo", environment, map);
    return map;
  }
}
