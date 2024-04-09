/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.core.registry.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.storage.Storage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
@ConditionalOnProperty("spinnaker.config.input.gcs.enabled")
public class GoogleProfileReader implements ProfileReader {
  @Autowired String spinconfigBucket;

  @Autowired Storage applicationDefaultGoogleStorage;

  @Autowired Storage unauthenticatedGoogleStorage;

  @Autowired ObjectMapper relaxedObjectMapper;

  @Autowired ApplicationContext applicationContext;

  private Yaml getYamlParser() {
    return applicationContext.getBean(Yaml.class);
  }

  public InputStream readProfile(String artifactName, String version, String profileName)
      throws IOException {
    String path = profilePath(artifactName, version, profileName);
    return getContents(path);
  }

  public BillOfMaterials readBom(String version) throws IOException {
    String bomName = bomPath(version);

    return relaxedObjectMapper.convertValue(
        getYamlParser().load(getContents(bomName)), BillOfMaterials.class);
  }

  public Versions readVersions() throws IOException {
    return relaxedObjectMapper.convertValue(
        getYamlParser().load(getContents("versions.yml")), Versions.class);
  }

  public InputStream readArchiveProfile(String artifactName, String version, String profileName)
      throws IOException {
    return readProfile(artifactName, version, profileName + ".tar.gz");
  }

  String profilePath(String artifactName, String version, String profileFileName) {
    return String.join("/", artifactName, version, profileFileName);
  }

  String bomPath(String version) {
    return String.join("/", "bom", version + ".yml");
  }

  private InputStream getContents(String objectName) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    log.info("Getting object contents of " + objectName);

    try {
      applicationDefaultGoogleStorage
          .objects()
          .get(spinconfigBucket, objectName)
          .executeMediaAndDownloadTo(output);
    } catch (IOException e) {
      log.debug(
          "Getting object contents of {} failed. Retrying with no authentication.", objectName, e);
      output = new ByteArrayOutputStream();
      unauthenticatedGoogleStorage
          .objects()
          .get(spinconfigBucket, objectName)
          .executeMediaAndDownloadTo(output);
    }

    return new ByteArrayInputStream(output.toByteArray());
  }
}
