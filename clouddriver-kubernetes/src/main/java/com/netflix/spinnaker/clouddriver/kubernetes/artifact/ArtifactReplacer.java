/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@ParametersAreNonnullByDefault
@Slf4j
public class ArtifactReplacer {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Configuration configuration =
      Configuration.builder()
          .jsonProvider(new JacksonJsonNodeJsonProvider())
          .mappingProvider(new JacksonMappingProvider())
          .build();

  private final ImmutableList<Replacer> replacers;

  public ArtifactReplacer(Collection<Replacer> replacers) {
    this.replacers = ImmutableList.copyOf(replacers);
  }

  private static ImmutableList<Artifact> filterKubernetesArtifactsByNamespaceAndAccount(
      String namespace, String account, List<Artifact> artifacts) {
    return artifacts.stream()
        // Keep artifacts that either aren't k8s, or are in the same namespace and account as our
        // manifest
        .filter(
            a -> {
              String type = a.getType();
              if (Strings.isNullOrEmpty(type)) {
                log.warn("Artifact {} without a type, ignoring", a);
                return false;
              }

              if (!type.startsWith("kubernetes/")) {
                return true;
              }

              boolean locationMatches;
              String location = a.getLocation();
              if (Strings.isNullOrEmpty(location)) {
                locationMatches = Strings.isNullOrEmpty(namespace);
              } else {
                locationMatches = location.equals(namespace);
              }

              boolean accountMatches;
              String artifactAccount = getAccount(a);
              // If the artifact fails to provide an account, we'll assume this was unintentional
              // and match anyways
              accountMatches =
                  Strings.isNullOrEmpty(artifactAccount) || artifactAccount.equals(account);

              return accountMatches && locationMatches;
            })
        .collect(toImmutableList());
  }

  private static String getAccount(Artifact artifact) {
    return Strings.nullToEmpty((String) artifact.getMetadata("account"));
  }

  @Nonnull
  public ReplaceResult replaceAll(
      KubernetesManifest input, List<Artifact> artifacts, String namespace, String account) {
    log.debug("Doing replacement on {} using {}", input, artifacts);
    ImmutableList<Artifact> filteredArtifacts =
        filterKubernetesArtifactsByNamespaceAndAccount(namespace, account, artifacts);
    DocumentContext document;
    try {
      document = JsonPath.using(configuration).parse(mapper.writeValueAsString(input));
    } catch (JsonProcessingException e) {
      log.error("Malformed manifest", e);
      throw new RuntimeException(e);
    }

    ImmutableSet.Builder<Artifact> replacedArtifacts = new ImmutableSet.Builder<>();
    replacers.forEach(
        replacer ->
            replacedArtifacts.addAll(replacer.replaceArtifacts(document, filteredArtifacts)));

    try {
      return new ReplaceResult(
          mapper.readValue(document.jsonString(), KubernetesManifest.class),
          replacedArtifacts.build());
    } catch (IOException e) {
      log.error("Malformed Document Context", e);
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public ImmutableSet<Artifact> findAll(KubernetesManifest input) {
    DocumentContext document;
    try {
      document = JsonPath.using(configuration).parse(mapper.writeValueAsString(input));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Malformed manifest", e);
    }

    return replacers.stream()
        .map(
            r -> {
              try {
                return r.getArtifacts(document);
              } catch (Exception e) {
                // This happens when a manifest isn't fully defined (e.g. not all properties are
                // there)
                log.debug(
                    "Failure converting artifacts for {} using {} (skipping)",
                    input.getFullResourceName(),
                    r,
                    e);
                return ImmutableList.<Artifact>of();
              }
            })
        .flatMap(Collection::stream)
        .collect(toImmutableSet());
  }

  @Value
  public static class ReplaceResult {
    private final KubernetesManifest manifest;
    private final ImmutableSet<Artifact> boundArtifacts;
  }
}
