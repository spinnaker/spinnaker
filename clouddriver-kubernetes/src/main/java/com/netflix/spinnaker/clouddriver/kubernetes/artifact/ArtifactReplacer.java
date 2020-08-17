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
import com.google.common.collect.ImmutableCollection;
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
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
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

  private static ImmutableList<Artifact> filterArtifacts(
      @Nonnull String namespace, @Nonnull String account, List<Artifact> artifacts) {
    return artifacts.stream()
        .filter(a -> !Strings.isNullOrEmpty(a.getType()))
        .filter(nonKubernetes().or(namespaceMatches(namespace).and(accountMatches(account))))
        .collect(toImmutableList());
  }

  private static Predicate<Artifact> nonKubernetes() {
    return a -> !a.getType().startsWith("kubernetes/");
  }

  private static Predicate<Artifact> namespaceMatches(@Nonnull String namespace) {
    return a -> Strings.nullToEmpty(a.getLocation()).equals(namespace);
  }

  private static Predicate<Artifact> accountMatches(@Nonnull String account) {
    return a -> {
      String artifactAccount = Strings.nullToEmpty((String) a.getMetadata("account"));
      // If the artifact fails to provide an account, assume this was unintentional and match
      // anyways
      return artifactAccount.isEmpty() || artifactAccount.equals(account);
    };
  }

  @Nonnull
  public ReplaceResult replaceAll(
      KubernetesManifest input,
      List<Artifact> artifacts,
      @Nonnull String namespace,
      @Nonnull String account) {
    log.debug("Doing replacement on {} using {}", input, artifacts);
    DocumentContext document;
    try {
      document = JsonPath.using(configuration).parse(mapper.writeValueAsString(input));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Malformed manifest", e);
    }

    ImmutableList<Artifact> filteredArtifacts = filterArtifacts(namespace, account, artifacts);
    ImmutableSet.Builder<Artifact> replacedArtifacts = ImmutableSet.builder();
    for (Replacer replacer : replacers) {
      ImmutableCollection<Artifact> replaced =
          replacer.replaceArtifacts(document, filteredArtifacts);
      replacedArtifacts.addAll(replaced);
    }

    try {
      return new ReplaceResult(
          mapper.readValue(document.jsonString(), KubernetesManifest.class),
          replacedArtifacts.build());
    } catch (IOException e) {
      throw new UncheckedIOException("Malformed manifest", e);
    }
  }

  @Nonnull
  public ImmutableSet<Artifact> findAll(KubernetesManifest input) {
    DocumentContext document;
    try {
      document = JsonPath.using(configuration).parse(mapper.writeValueAsString(input));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Malformed manifest", e);
    }

    return replacers.stream()
        .flatMap(
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
                return Stream.empty();
              }
            })
        .collect(toImmutableSet());
  }

  @Value
  public static class ReplaceResult {
    private final KubernetesManifest manifest;
    private final ImmutableSet<Artifact> boundArtifacts;
  }
}
