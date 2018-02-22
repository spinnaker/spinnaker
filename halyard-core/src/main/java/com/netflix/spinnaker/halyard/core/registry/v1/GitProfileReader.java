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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

@Component
@Slf4j
public class GitProfileReader implements ProfileReader {
  @Autowired
  String gitRoot;

  private final static String HALCONFIG_DIR = "halconfig";

  @Override
  public InputStream readProfile(String artifactName, String version, String profileName) throws IOException {
    return getContents(profilePath(artifactName, version, profileName));
  }

  @Override
  public BillOfMaterials readBom(String version) throws IOException {
    if (!Versions.isBranch(version)) {
      throw new IllegalArgumentException("Version must be a branch in the git profile reader");
    }
    String branch = Versions.fromBranch(version);

    BillOfMaterials.Artifact artifact = new BillOfMaterials.Artifact();
    artifact.setCommit(branch);
    artifact.setVersion(version);

    BillOfMaterials.Services services = new BillOfMaterials.Services();
    services.setDefaultArtifact(artifact);

    BillOfMaterials.Dependencies dependencies = new BillOfMaterials.Dependencies();
    dependencies.setDefaultArtifact(artifact);

    BillOfMaterials bom = new BillOfMaterials();
    bom.setDependencies(dependencies);
    bom.setVersion(version);
    bom.setServices(services);

    return bom;
  }

  @Override
  public Versions readVersions() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream readArchiveProfile(String artifactName, String version, String profileName) throws IOException {
    throw new UnsupportedOperationException();
  }

  private String profilePath(String artifactName, String version, String profileFileName) {
    return Paths.get(gitRoot, artifactName, HALCONFIG_DIR, profileFileName).toString();
  }

  private InputStream getContents(String objectName) throws IOException {
    log.info("Getting file contents of " + objectName);
    return new FileInputStream(objectName);
  }
}
