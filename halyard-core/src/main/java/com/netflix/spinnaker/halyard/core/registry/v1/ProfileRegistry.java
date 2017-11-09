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
 */

package com.netflix.spinnaker.halyard.core.registry.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;

@Component
@Slf4j
public class ProfileRegistry {
  @Autowired
  GoogleProfileRegistry googleProfileRegistry;

  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper relaxedObjectMapper;

  public InputStream readProfile(String artifactName, String version, String profileName) throws IOException {
    String path = googleProfileRegistry.profilePath(artifactName, version, profileName);
    return googleProfileRegistry.getObjectContents(path);
  }

  public BillOfMaterials readBom(String version) throws IOException {
    String bomName = googleProfileRegistry.bomPath(version);

    return relaxedObjectMapper.convertValue(
        yamlParser.load(googleProfileRegistry.getObjectContents(bomName)),
        BillOfMaterials.class
    );
  }

  public Versions readVersions() throws IOException {
    return relaxedObjectMapper.convertValue(
        yamlParser.load(googleProfileRegistry.getObjectContents("versions.yml")),
        Versions.class
    );
  }

  public InputStream readArchiveProfile(String artifactName, String version, String profileName) throws IOException {
    return readProfile(artifactName, version, profileName + ".tar.gz");
  }
}
