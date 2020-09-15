/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_TEMPLATES;
import static java.util.stream.Collectors.toSet;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesResult;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AmazonLaunchTemplateCachingAgent implements CachingAgent, AccountAware {
  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final ObjectMapper objectMapper;
  private final String region;

  private static final String[] DEFAULT_VERSIONS = new String[] {"$Default", "$Latest"};

  private static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};
  private static final Set<AgentDataType> types =
      new HashSet<>(Collections.singletonList(AUTHORITATIVE.forType(LAUNCH_TEMPLATES.getNs())));

  public AmazonLaunchTemplateCachingAgent(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      ObjectMapper objectMapper,
      Registry registry) {
    this.amazonClientProvider = amazonClientProvider;
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    final AmazonEC2 ec2 = amazonClientProvider.getAmazonEC2(account, region);
    final List<LaunchTemplate> launchTemplates = getLaunchTemplates(ec2);
    final List<LaunchTemplateVersion> launchTemplateVersions =
        getLaunchTemplateVersions(ec2, DEFAULT_VERSIONS);
    final List<CacheData> cachedData = new ArrayList<>();
    for (LaunchTemplate launchTemplate : launchTemplates) {
      Set<LaunchTemplateVersion> versions =
          launchTemplateVersions.stream()
              .filter(t -> t.getLaunchTemplateId().equals(launchTemplate.getLaunchTemplateId()))
              .collect(toSet());

      // store the latest template version info
      LaunchTemplateVersion latest =
          versions.stream()
              .filter(v -> v.getVersionNumber().equals(launchTemplate.getLatestVersionNumber()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "No latest version found for template %s", launchTemplate)));

      String key =
          Keys.getLaunchTemplateKey(
              launchTemplate.getLaunchTemplateName(), account.getName(), region);
      Map<String, Object> attributes = objectMapper.convertValue(launchTemplate, ATTRIBUTES);

      attributes.put("application", Keys.parse(key).get("application"));
      attributes.put("latestVersion", latest);

      // include version info
      attributes.put("versions", versions);

      Set<String> images =
          versions.stream()
              .map(
                  i ->
                      Keys.getImageKey(
                          i.getLaunchTemplateData().getImageId(), account.getName(), region))
              .collect(Collectors.toSet());

      Map<String, Collection<String>> relationships = Collections.singletonMap(IMAGES.ns, images);
      cachedData.add(new DefaultCacheData(key, attributes, relationships));
    }

    return new DefaultCacheResult(Collections.singletonMap(LAUNCH_TEMPLATES.ns, cachedData));
  }

  /** Gets a list of ec2 Launch templates */
  private List<LaunchTemplate> getLaunchTemplates(AmazonEC2 ec2) {
    final List<LaunchTemplate> launchTemplates = new ArrayList<>();
    final DescribeLaunchTemplatesRequest request = new DescribeLaunchTemplatesRequest();
    while (true) {
      final DescribeLaunchTemplatesResult result = ec2.describeLaunchTemplates(request);
      launchTemplates.addAll(result.getLaunchTemplates());
      if (result.getNextToken() != null) {
        request.withNextToken(result.getNextToken());
      } else {
        break;
      }
    }

    return launchTemplates;
  }

  /** Gets a list of ec2 Launch template versions for a Launch template */
  private List<LaunchTemplateVersion> getLaunchTemplateVersions(AmazonEC2 ec2, String... versions) {
    final List<LaunchTemplateVersion> launchTemplateVersions = new ArrayList<>();
    final DescribeLaunchTemplateVersionsRequest request =
        new DescribeLaunchTemplateVersionsRequest().withVersions(versions);
    while (true) {
      final DescribeLaunchTemplateVersionsResult result =
          ec2.describeLaunchTemplateVersions(request);
      launchTemplateVersions.addAll(result.getLaunchTemplateVersions());
      if (result.getNextToken() != null) {
        request.withNextToken(result.getNextToken());
      } else {
        break;
      }
    }

    return launchTemplateVersions;
  }

  @Override
  public String getAgentType() {
    return String.format("%s/%s/%s", account.getName(), region, getClass().getSimpleName());
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }
}
