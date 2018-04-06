/*
 * Copyright 2018 Praekelt.org
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;


import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;

import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LocalDiskProfileReader implements ProfileReader {
  @Autowired
  String localBomPath;

  @Autowired
  ObjectMapper relaxedObjectMapper;

  @Autowired
  Yaml yamlParser;

  private final static String HALCONFIG_DIR = "halconfig";

  @Override
  public InputStream readProfile(String artifactName, String version, String profileName) throws IOException {
    String path = profilePath(artifactName, profileName);
    return getContents(path);
  }

  @Override
  public BillOfMaterials readBom(String version) throws IOException {
      if (!Versions.isLocal(version)) {
        throw new IllegalArgumentException("Versions using a local BOM must be prefixed with \"local:\"");
      }
      String versionName = Versions.fromLocal(version);
      String bomName = bomPath(versionName);
      return relaxedObjectMapper.convertValue(
        yamlParser.load(getContents(bomName)),
        BillOfMaterials.class
      );
  }

  @Override
  public Versions readVersions() throws IOException {
      throw new UnsupportedOperationException();
  }

  @Override
  public InputStream readArchiveProfile(String artifactName, String version, String profileName) throws IOException {
      Path profilePath = Paths.get(profilePath(artifactName, profileName));

      ByteArrayOutputStream os = new ByteArrayOutputStream();
      TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(os);

      ArrayList<Path> filePathsToAdd =
        java.nio.file.Files.walk(profilePath, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)
        .filter(path -> path.toFile().isFile())
        .collect(Collectors.toCollection(ArrayList::new));

      for (Path path : filePathsToAdd) {
      TarArchiveEntry tarEntry = new TarArchiveEntry(path.toFile(), profilePath.relativize(path).toString());
      tarArchive.putArchiveEntry(tarEntry);
      IOUtils.copy(Files.newInputStream(path), tarArchive);
      tarArchive.closeArchiveEntry();
      }

      tarArchive.finish();
      tarArchive.close();

      return new ByteArrayInputStream(os.toByteArray());
  }

  private String profilePath(String artifactName, String profileFileName) {
      return Paths.get(localBomPath, artifactName, HALCONFIG_DIR, profileFileName).toString();
  }

  String bomPath(String version) {
    return Paths.get(localBomPath, String.join("/", "bom", version + ".yml")).toString();
  }

  private InputStream getContents(String objectName) throws IOException {
      log.info("Getting file contents of " + objectName);
      return new FileInputStream(objectName);
    }

  }