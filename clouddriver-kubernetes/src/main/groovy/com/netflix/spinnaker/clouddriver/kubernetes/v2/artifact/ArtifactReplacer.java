/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ArtifactReplacer {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Configuration configuration = Configuration.builder()
      .jsonProvider(new JacksonJsonNodeJsonProvider())
      .mappingProvider(new JacksonMappingProvider())
      .build();

  List<Replacer> replacers = new ArrayList<>();

  public ArtifactReplacer addReplacer(Replacer replacer) {
    replacers.add(replacer);
    return this;
  }

  private static List<Artifact> filterKubernetesArtifactsByNamespaceAndAccount(String namespace, String account, List<Artifact> artifacts) {
    return artifacts.stream()
        // Keep artifacts that either aren't k8s, or are in the same namespace and account as our manifest
        .filter(a -> {
          String type = a.getType();
          if (StringUtils.isEmpty(type)) {
            log.warn("Artifact {} without a type, ignoring", a);
            return false;
          }

          if (!type.startsWith("kubernetes/")) {
            return true;
          }

          boolean locationMatches;
          String location = a.getLocation();
          if (StringUtils.isEmpty(location)) {
            locationMatches = StringUtils.isEmpty(namespace);
          } else {
            locationMatches = location.equals(namespace);
          }

          boolean accountMatches;
          String artifactAccount = KubernetesArtifactConverter.getAccount(a);
          // If the artifact fails to provide an account, we'll assume this was unintentional and match anyways
          accountMatches = StringUtils.isEmpty(artifactAccount) || artifactAccount.equals(account);

          return accountMatches && locationMatches;
        })
        .collect(Collectors.toList());
  }

  public ReplaceResult replaceAll(KubernetesManifest input, List<Artifact> artifacts, String namespace, String account) {
    log.debug("Doing replacement on {} using {}", input, artifacts);
    // final to use in below lambda
    final List<Artifact> finalArtifacts = filterKubernetesArtifactsByNamespaceAndAccount(namespace, account, artifacts);
    DocumentContext document;
    try {
      document = JsonPath.using(configuration).parse(mapper.writeValueAsString(input));
    } catch (JsonProcessingException e) {
      log.error("Malformed manifest", e);
      throw new RuntimeException(e);
    }

    Set<Artifact> replacedArtifacts = replacers.stream()
        .map(r -> finalArtifacts.stream()
            .filter(a -> r.replaceIfPossible(document, a))
            .collect(Collectors.toSet()))
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());

    try {
      return ReplaceResult.builder()
          .manifest(mapper.readValue(document.jsonString(), KubernetesManifest.class))
          .boundArtifacts(replacedArtifacts)
          .build();
    } catch (IOException e) {
      log.error("Malformed Document Context", e);
      throw new RuntimeException(e);
    }
  }

  public Set<Artifact> findAll(KubernetesManifest input) {
    DocumentContext document;
    try {
      document = JsonPath.using(configuration).parse(mapper.writeValueAsString(input));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Malformed manifest", e);
    }

    return replacers.stream()
        .map(r -> {
              try {
                return ((List<String>) mapper.convertValue(r.findAll(document), new TypeReference<List<String>>() { }))
                    .stream()
                    .map(s -> {
                          String nameFromReference = r.getNameFromReference(s);
                          String name = nameFromReference == null ? s : nameFromReference;
                          if (r.namePattern == null || nameFromReference != null) {
                            return Artifact.builder()
                              .type(r.getType().toString())
                              .reference(s)
                              .name(name)
                              .build();
                          } else {
                            return null;
                          }
                        }
                    ).filter(Objects::nonNull);
              } catch (Exception e) {
                // This happens when a manifest isn't fully defined (e.g. not all properties are there)
                log.debug("Failure converting artifacts for {} using {} (skipping)", input.getFullResourceName(), r, e);
                return Stream.<Artifact> empty();
              }
            }
        ).flatMap(x -> x)
        .collect(Collectors.toSet());
  }

  @Slf4j
  @Builder
  @AllArgsConstructor
  public static class Replacer {
    private final String replacePath;
    private final String findPath;
    private final Pattern namePattern; // the first group should be the artifact name
    private final Function<String, String> nameFromReference;

    @Getter
    private final ArtifactTypes type;

    private static String substituteField(String result, String fieldName, String field) {
      field = field == null ? "" : field;
      return result.replace("{%" + fieldName + "%}", field);
    }

    private static String processPath(String path, Artifact artifact) {
      String result = substituteField(path, "name", artifact.getName());
      result = substituteField(result, "type", artifact.getType());
      result = substituteField(result, "version", artifact.getVersion());
      result = substituteField(result, "reference", artifact.getReference());
      return result;
    }

    ArrayNode findAll(DocumentContext obj) {
       return obj.read(findPath);
    }

    String getNameFromReference(String reference) {
      if (nameFromReference != null) {
        return nameFromReference.apply(reference);
      } else if (namePattern != null) {
        Matcher m = namePattern.matcher(reference);
        if (m.find() && m.groupCount() > 0 && StringUtils.isNotEmpty(m.group(1))) {
          return m.group(1);
        } else {
          return null;
        }
      } else {
        return null;
      }
    }

    boolean replaceIfPossible(DocumentContext obj, Artifact artifact) {
      if (artifact == null || StringUtils.isEmpty(artifact.getType())) {
        throw new IllegalArgumentException("Artifact and artifact type must be set.");
      }

      if (!artifact.getType().equals(type.toString())) {
        return false;
      }

      String jsonPath = processPath(replacePath, artifact);

      log.debug("Processed jsonPath == {}", jsonPath);

      Object get;
      try {
        get = obj.read(jsonPath);
      } catch (PathNotFoundException e) {
        return false;
      }
      if (get == null || (get instanceof ArrayNode && ((ArrayNode) get).size() == 0)) {
        return false;
      }

      log.info("Found valid swap for " + artifact + " using " + jsonPath + ": " + get);
      obj.set(jsonPath, artifact.getReference());

      return true;
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ReplaceResult {
    private KubernetesManifest manifest;
    private Set<Artifact> boundArtifacts = new HashSet<>();
  }
}
