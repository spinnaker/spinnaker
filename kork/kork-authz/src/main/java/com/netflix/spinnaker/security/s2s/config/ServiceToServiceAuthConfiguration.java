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

import com.netflix.spinnaker.security.s2s.ServiceCallerEnforcementAspect;
import com.netflix.spinnaker.security.s2s.ServiceCallerResolver;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import com.netflix.spinnaker.security.s2s.filter.ServiceCallerAuthenticationFilter;
import com.netflix.spinnaker.security.s2s.provider.HeaderServiceCallerResolver;
import com.netflix.spinnaker.security.s2s.provider.K8sServiceAccountTokenResolver;
import com.netflix.spinnaker.security.s2s.provider.X509SubjectServiceCallerResolver;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires generic, mesh-wide service-to-service authentication.
 *
 * <p>Import this from a service's security configuration to give it the {@link
 * ServiceCallerAuthenticationFilter} and the {@link ServiceCallerEnforcementAspect} that enforces
 * {@link com.netflix.spinnaker.security.s2s.AllowServiceCallers}. All beans are inert while {@code
 * authz.s2s.enabled=false} (the default), so importing it is a no-op until an operator opts in.
 * Only the transport <em>mechanism</em> is configurable here; caller→endpoint policy is codified.
 */
@Configuration
@EnableConfigurationProperties(ServiceToServiceProperties.class)
public class ServiceToServiceAuthConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(ServiceToServiceAuthConfiguration.class);

  @Bean
  public ServiceCallerResolver serviceCallerResolver(ServiceToServiceProperties properties) {
    if (!properties.isEnabled()) {
      return ServiceCallerResolver.disabled();
    }
    SpinnakerServiceMapper mapper = new SpinnakerServiceMapper(properties.getServiceNamePrefix());
    switch (properties.getProvider()) {
      case X509_SUBJECT:
        log.info("Service-to-service auth enabled: provider=x509-subject");
        return new X509SubjectServiceCallerResolver(
            Pattern.compile(properties.getSubjectRegex()), mapper);
      case HEADER:
        log.info("Service-to-service auth enabled: provider=header ({})", properties.getHeader());
        return new HeaderServiceCallerResolver(properties.getHeader(), mapper);
      case K8S_SA_TOKEN:
        return buildK8sResolver(properties, mapper);
      case NONE:
      default:
        log.warn("Service-to-service auth enabled but provider=none; no caller will be resolved");
        return ServiceCallerResolver.disabled();
    }
  }

  private ServiceCallerResolver buildK8sResolver(
      ServiceToServiceProperties properties, SpinnakerServiceMapper mapper) {
    ServiceToServiceProperties.K8s k8s = properties.getK8s();
    if (k8s.getAudiences() == null || k8s.getAudiences().isEmpty()) {
      // Require an audience so a projected token minted for another audience (e.g. the Kubernetes
      // API server) cannot be replayed against Spinnaker.
      log.error(
          "Service-to-service auth provider=k8s-sa-token requires authz.s2s.k8s.audiences "
              + "(the audience the projected token is bound to); no caller will be resolved until "
              + "it is set");
      return ServiceCallerResolver.disabled();
    }
    try {
      log.info(
          "Service-to-service auth enabled: provider=k8s-sa-token (ns={}, jwks={})",
          k8s.getNamespace(),
          k8s.getJwksUri());
      return K8sServiceAccountTokenResolver.build(
          k8s.getJwksUri(),
          k8s.getIssuer(),
          k8s.getAudiences(),
          k8s.getJwsAlgorithms(),
          k8s.getTokenHeader(),
          k8s.getNamespace(),
          mapper,
          k8s.getJwksCaCertPath(),
          k8s.getJwksTokenPath(),
          Duration.ofSeconds(k8s.getTokenRefreshSeconds()),
          k8s.getJwksConnectTimeoutMs(),
          k8s.getJwksReadTimeoutMs(),
          k8s.getJwksSizeLimitBytes());
    } catch (GeneralSecurityException | IOException e) {
      log.error(
          "Cannot initialize k8s-sa-token caller resolution (jwks-uri '{}', CA bundle '{}'): {}. "
              + "No caller will be resolved until this is fixed.",
          k8s.getJwksUri(),
          k8s.getJwksCaCertPath(),
          e.getMessage());
      return ServiceCallerResolver.disabled();
    }
  }

  @Bean
  public ServiceCallerAuthenticationFilter serviceCallerAuthenticationFilter(
      ServiceCallerResolver serviceCallerResolver, ServiceToServiceProperties properties) {
    return new ServiceCallerAuthenticationFilter(serviceCallerResolver, properties);
  }

  @Bean
  public ServiceCallerEnforcementAspect serviceCallerEnforcementAspect(
      ServiceToServiceProperties properties, ServiceCallerResolver serviceCallerResolver) {
    return new ServiceCallerEnforcementAspect(properties, serviceCallerResolver);
  }
}
