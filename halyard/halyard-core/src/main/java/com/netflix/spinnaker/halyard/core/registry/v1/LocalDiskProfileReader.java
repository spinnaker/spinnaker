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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
public class LocalDiskProfileReader implements ProfileReader {
  @Autowired String localBomPath;

  @Autowired ObjectMapper relaxedObjectMapper;

  @Autowired ApplicationContext applicationContext;

  private static final String HALCONFIG_DIR = "halconfig";

  private Yaml getYamlParser() {
    return applicationContext.getBean(Yaml.class);
  }

  @Override
  public InputStream readProfile(String artifactName, String version, String profileName)
      throws IOException {
    version = version.substring("local:".length());
    try {
      String path = profilePath(artifactName, version, profileName);
      return getContents(path);
    } catch (IOException e) {
      log.debug("Failed to get profile, retrying default location", e);
      String path = defaultProfilePath(artifactName, profileName);
      return getContents(path);
    }
  }

  @Override
  public BillOfMaterials readBom(String version) throws IOException {
    if (!Versions.isLocal(version)) {
      throw new IllegalArgumentException(
          "Versions using a local BOM must be prefixed with \"local:\"");
    }
    String versionName = Versions.fromLocal(version);
    String bomName = bomPath(versionName);
    return relaxedObjectMapper.convertValue(
        getYamlParser().load(getContents(bomName)), BillOfMaterials.class);
  }

  @Override
  public Versions readVersions() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream readArchiveProfile(String artifactName, String version, String profileName)
      throws IOException {
    version = version.substring("local:".length());
    String fileName = profileName + ".tar.gz";

    try {
      Path profilePath = Paths.get(profilePath(artifactName, version, fileName));
      return readArchiveProfileFrom(profilePath);
    } catch (IOException e) {
      log.debug("Failed to get archive profile, retrying default location", e);
      Path profilePath = Paths.get(defaultProfilePath(artifactName, fileName));
      return readArchiveProfileFrom(profilePath);
    }
  }

  public InputStream readArchiveProfileFrom(Path profilePath) throws IOException {

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(os);

    ArrayList<Path> filePathsToAdd =
        java.nio.file.Files.walk(profilePath, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)
            .filter(path -> path.toFile().isFile())
            .collect(Collectors.toCollection(ArrayList::new));

    for (Path path : filePathsToAdd) {
      TarArchiveEntry tarEntry =
          new TarArchiveEntry(path.toFile(), profilePath.relativize(path).toString());
      tarArchive.putArchiveEntry(tarEntry);
      IOUtils.copy(Files.newInputStream(path), tarArchive);
      tarArchive.closeArchiveEntry();
    }

    tarArchive.finish();
    tarArchive.close();

    return new ByteArrayInputStream(os.toByteArray());
  }

  private String profilePath(String artifactName, String version, String profileFileName) {
    return Paths.get(localBomPath, artifactName, version, profileFileName).toString();
  }

  private String defaultProfilePath(String artifactName, String profileFileName) {
    return Paths.get(localBomPath, artifactName, profileFileName).toString();
  }

  String bomPath(String version) {
    return Paths.get(localBomPath, String.join("/", "bom", version + ".yml")).toString();
  }

  private InputStream getContents(String objectName) throws IOException {
    log.info("Getting file contents of " + objectName);
    return new FileInputStream(objectName);
  }
}
