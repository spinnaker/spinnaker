/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s.config;

import com.netflix.spinnaker.security.s2s.client.ProjectedServiceAccountTokenSource;
import com.netflix.spinnaker.security.s2s.client.ServiceIdentityInterceptor;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the <em>calling</em> side of service-to-service authentication, so this service can prove
 * its identity to peers. {@link ServiceToServiceAuthConfiguration} covers the receiving side; a
 * deployment needs both, and enabling only the receiving side is what makes every {@code
 * AllowServiceCallers} endpoint return 403.
 *
 * <p>Only the {@code k8s-sa-token} provider needs anything here: with {@code x509-subject} the
 * identity is the mTLS client certificate presented during the handshake, and with {@code header}
 * the service mesh injects it — in both cases the transport carries the identity and the
 * application sends nothing.
 *
 * <p>The interceptor is exposed as a plain bean rather than a global OkHttp customizer so callers
 * attach it only to clients that target other Spinnaker services; see {@link
 * ServiceIdentityInterceptor} for why.
 */
@Configuration
@EnableConfigurationProperties(ServiceToServiceProperties.class)
public class ServiceIdentityClientConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(ServiceIdentityClientConfiguration.class);

  /**
   * Always defined so callers can inject it unconditionally. When service-to-service auth is off,
   * or the provider carries identity at the transport, the token source is pointed at nothing and
   * the interceptor becomes a pass-through — matching the "inert until opted in" contract of the
   * rest of {@code kork-authz}.
   */
  @Bean
  public ServiceIdentityInterceptor serviceIdentityInterceptor(
      ServiceToServiceProperties properties) {
    ServiceToServiceProperties.K8s k8s = properties.getK8s();
    boolean active =
        properties.isEnabled()
            && properties.getProvider() == ServiceToServiceProperties.Provider.K8S_SA_TOKEN;

    if (!active) {
      return new ServiceIdentityInterceptor(
          ProjectedServiceAccountTokenSource.disabled(), k8s.getTokenHeader());
    }

    log.info(
        "Outbound service identity enabled: presenting projected ServiceAccount token from {} on {}",
        k8s.getTokenPath(),
        k8s.getTokenHeader());

    return new ServiceIdentityInterceptor(
        new ProjectedServiceAccountTokenSource(
            Path.of(k8s.getTokenPath()), Duration.ofSeconds(k8s.getTokenRefreshSeconds())),
        k8s.getTokenHeader());
  }
}
