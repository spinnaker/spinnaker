/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests.cloudfoundry;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

@Component
public class CloudFoundryBakeManifestService
    extends BakeManifestService<CloudFoundryBakeManifestRequest> {

  private static final ImmutableSet<String> supportedTemplates =
      ImmutableSet.of(BakeManifestRequest.TemplateRenderer.CF.toString());
  private final ArtifactDownloader artifactDownloader;

  @Autowired
  public CloudFoundryBakeManifestService(
      JobExecutor jobExecutor, ArtifactDownloader artifactDownloader) {
    super(jobExecutor);
    this.artifactDownloader = artifactDownloader;
  }

  @Override
  public boolean handles(String type) {
    return supportedTemplates.contains(type);
  }

  @Override
  public Artifact bake(CloudFoundryBakeManifestRequest bakeManifestRequest) throws IOException {
    String pattern = "\\(\\((!?[-/\\.\\w\\pL\\]\\[]+)\\)\\)";

    Yaml yaml = new Yaml();

    String manifestTemplate =
        CharStreams.toString(
            new InputStreamReader(
                artifactDownloader.downloadArtifact(bakeManifestRequest.getManifestTemplate()),
                Charsets.UTF_8));

    Map<String, Object> vars = new HashMap<>();
    for (Artifact artifact : bakeManifestRequest.getVarsArtifacts()) {
      InputStream inputStream = artifactDownloader.downloadArtifact(artifact);
      vars.putAll(yaml.load(inputStream));
      inputStream.close();
    }
    vars = flatten(vars);

    Set<String> unresolvedKeys = new HashSet<>();
    Matcher m = Pattern.compile(pattern).matcher(manifestTemplate);

    while (m.find()) {
      String key = m.group().substring(2, m.group().length() - 2);
      if (vars.get(key) != null) {
        manifestTemplate = manifestTemplate.replace(m.group(), (String) vars.get(key));
      } else {
        unresolvedKeys.add(m.group());
      }
    }

    if (unresolvedKeys.size() > 0) {
      throw new IllegalArgumentException(
          "Unable to resolve values for the following keys: \n"
              + String.join("\n ", unresolvedKeys));
    }

    return Artifact.builder()
        .type("embedded/base64")
        .name(bakeManifestRequest.getOutputArtifactName())
        .reference(Base64.getEncoder().encodeToString(manifestTemplate.getBytes()))
        .build();
  }

  @Override
  public Class<CloudFoundryBakeManifestRequest> requestType() {
    return CloudFoundryBakeManifestRequest.class;
  }

  /*
   * The following four methods are influenced by @author Mark Paluch
   * The full class is here:
   * https://github.com/spring-projects/spring-vault/blob/master/spring-vault-core/src/main/java/org/springframework/vault/support/JsonMapFlattener.java
   */
  private Map<String, Object> flatten(Map<String, ? extends Object> inputMap) {

    Map<String, Object> resultMap = new HashMap<>();

    doFlatten("", inputMap.entrySet().iterator(), resultMap, UnaryOperator.identity());

    return resultMap;
  }

  private void doFlatten(
      String propertyPrefix,
      Iterator<? extends Map.Entry<String, ?>> inputMap,
      Map<String, ? extends Object> resultMap,
      Function<Object, Object> valueTransformer) {

    if (StringUtils.hasText(propertyPrefix)) {
      propertyPrefix = propertyPrefix + ".";
    }

    while (inputMap.hasNext()) {

      Map.Entry<String, ? extends Object> entry = inputMap.next();
      flattenElement(
          propertyPrefix.concat(entry.getKey()), entry.getValue(), resultMap, valueTransformer);
    }
  }

  private void flattenElement(
      String propertyPrefix,
      @Nullable Object source,
      Map<String, ?> resultMap,
      Function<Object, Object> valueTransformer) {

    if (source instanceof Iterable) {
      flattenCollection(propertyPrefix, (Iterable<Object>) source, resultMap, valueTransformer);
      return;
    }

    if (source instanceof Map) {
      doFlatten(
          propertyPrefix,
          ((Map<String, ?>) source).entrySet().iterator(),
          resultMap,
          valueTransformer);
      return;
    }

    ((Map) resultMap).put(propertyPrefix, valueTransformer.apply(source));
  }

  private void flattenCollection(
      String propertyPrefix,
      Iterable<Object> iterable,
      Map<String, ?> resultMap,
      Function<Object, Object> valueTransformer) {

    int counter = 0;

    for (Object element : iterable) {
      flattenElement(propertyPrefix + "[" + counter + "]", element, resultMap, valueTransformer);
      counter++;
    }
  }
}
