/*
 * Copyright 2019 Armory, Inc.
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
 */

package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KustomizeTemplateUtils extends TemplateUtils {
  private final KustomizationFileReader kustomizationFileReader;

  public KustomizeTemplateUtils(
      KustomizationFileReader kustomizationFileReader, ClouddriverService clouddriverService) {
    super(clouddriverService);
    this.kustomizationFileReader = kustomizationFileReader;
  }

  public BakeRecipe buildBakeRecipe(
      BakeManifestEnvironment env, KustomizeBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());
    Artifact artifact = request.getInputArtifact();
    if (artifact == null) {
      throw new IllegalArgumentException("Exactly one input artifact must be provided to bake.");
    }
    String kustomizationfilename = FilenameUtils.getName(artifact.getReference());
    if (kustomizationfilename == null
        || (kustomizationfilename != null
            && !kustomizationfilename.toUpperCase().contains("KUSTOMIZATION"))) {
      throw new IllegalArgumentException("The inputArtifact should be a valid kustomization file.");
    }
    String referenceBaseURL = extractReferenceBase(artifact);
    Path templatePath = env.getStagingPath().resolve(artifact.getName());

    try {
      List<Artifact> artifacts = getArtifacts(artifact);
      for (Artifact ar : artifacts) {
        downloadArtifactToTmpFileStructure(env, ar, referenceBaseURL);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch kustomize files: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("kustomize");
    command.add("build");
    command.add(templatePath.getParent().toString());
    result.setCommand(command);

    return result;
  }

  protected void downloadArtifactToTmpFileStructure(
      BakeManifestEnvironment env, Artifact artifact, String referenceBaseURL) throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    Path artifactFileName = Paths.get(extractArtifactName(artifact, referenceBaseURL));
    Path artifactFilePath = env.getStagingPath().resolve(artifactFileName);
    // ensure file write doesn't break out of the staging directory ex. ../etc
    Path artifactParentDirectory = artifactFilePath.getParent();
    if (!pathIsWithinBase(env.getStagingPath(), artifactParentDirectory)) {
      throw new IllegalStateException("attempting to create a file outside of the staging path.");
    }
    Files.createDirectories(artifactParentDirectory);
    File newFile = artifactFilePath.toFile();
    downloadArtifact(artifact, newFile);
  }

  private boolean pathIsWithinBase(Path base, Path check) {
    return check.normalize().startsWith(base);
  }

  private List<Artifact> getArtifacts(Artifact artifact) {
    try {
      Set<String> files = getFilesFromArtifact(artifact);
      List<Artifact> artifacts =
          files.stream()
              .map(
                  f -> {
                    return Artifact.builder()
                        .reference(f)
                        .artifactAccount(artifact.getArtifactAccount())
                        .customKind(artifact.isCustomKind())
                        .location(artifact.getLocation())
                        .metadata(artifact.getMetadata())
                        .name(artifact.getName())
                        .provenance(artifact.getProvenance())
                        .type(artifact.getType())
                        .version(artifact.getVersion())
                        .build();
                  })
              .collect(Collectors.toList());
      return artifacts;
    } catch (IOException e) {
      throw new IllegalStateException("Error setting references in artifacts " + e.getMessage(), e);
    }
  }

  private String extractArtifactName(Artifact artifact, String base) {
    return artifact.getReference().replace(base, "");
  }

  private String extractReferenceBase(Artifact artifact) {
    // strip the base reference url to get the full path that the file should be written to
    // example: https://api.github.com/repos/org/repo/contents/kustomize.yml == kustomize.yml
    return artifact.getReference().replace(artifact.getName(), "");
  }

  /**
   * getFilesFromArtifact will use a single input artifact to determine the dependency tree of files
   * mentoined it's (and subsequent) kustomization file.
   */
  private HashSet<String> getFilesFromArtifact(Artifact artifact) throws IOException {
    // referenceBaseURL is the base URL without the artifacts name. works for github and bitbucket
    // artifacts,
    String referenceBaseURL = extractReferenceBase(artifact);
    // filename is the base name of the file
    String filename = FilenameUtils.getName(artifact.getName());
    // get the base directory of the original file. we'll use this to fetch sibling files
    Path base = Paths.get(artifact.getName()).getParent();
    return getFilesFromArtifact(artifact, referenceBaseURL, base, filename);
  }

  private HashSet<String> getFilesFromArtifact(
      Artifact artifact, String referenceBaseURL, Path base, String filename) throws IOException {
    // filesToDownload will be used to collect all of the files we need to fetch later
    HashSet<String> filesToDownload = new HashSet<>();

    String referenceBase = referenceBaseURL.concat(base.toString());
    Artifact testArtifact = artifactFromBase(artifact, referenceBase, base.toString());

    Kustomization kustomization = kustomizationFileReader.getKustomization(testArtifact, filename);
    // kustomization.setKustomizationFilename(base.resolve(filename).toString());
    // nonEvaluateFiles are files we know can't be references to other kustomizations
    // so we know they only need to be collected for download later
    Set<String> nonEvaluateFiles = kustomization.getFilesToDownload();
    filesToDownload.addAll(
        nonEvaluateFiles.stream()
            .map(f -> createUrlFromBase(referenceBase, f))
            .collect(Collectors.toSet()));
    filesToDownload.add(kustomization.getSelfReference());

    for (String evaluate : kustomization.getFilesToEvaluate()) {
      // we're assuming that files that look like folders are referencing
      // kustomizations above or below the current directory. if the file doesn't
      // look like a folder then we know it should be downloaded later.
      if (isFolder(evaluate)) {
        Path tmpBase = Paths.get(FilenameUtils.normalize(base.resolve(evaluate).toString()));
        artifact.setName(tmpBase.toString());
        filesToDownload.addAll(getFilesFromArtifact(artifact, referenceBaseURL, tmpBase, filename));
      } else {
        filesToDownload.add(referenceBase.concat(File.separator).concat(evaluate));
      }
    }
    return filesToDownload;
  }

  private String createUrlFromBase(String base, String path) {
    try {
      return new URI(base + "/").resolve(path).toString();
    } catch (Exception e) {
      throw new IllegalArgumentException("unable to form valid URL from " + base + " and " + path);
    }
  }

  private Artifact artifactFromBase(Artifact artifact, String reference, String name) {
    return Artifact.builder()
        .reference(reference)
        .name(name)
        .type(artifact.getType())
        .artifactAccount(artifact.getArtifactAccount())
        .build();
  }

  private boolean isFolder(String evaluate) {
    return FilenameUtils.getExtension(evaluate) == "";
  }
}
