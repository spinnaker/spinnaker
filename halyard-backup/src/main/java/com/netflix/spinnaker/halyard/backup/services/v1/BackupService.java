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

package com.netflix.spinnaker.halyard.backup.services.v1;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.services.v1.FileService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BackupService {
  @Autowired HalconfigParser halconfigParser;

  @Autowired HalconfigDirectoryStructure directoryStructure;

  @Autowired private FileService fileService;

  static String[] omitPaths = {"service-logs"};

  public void restore(String backupTar) {
    String halconfigDir = directoryStructure.getHalconfigDirectory();
    untarHalconfig(halconfigDir, backupTar);

    // This is only needed to support old backups where file paths were prefixed with
    // {%halconfig-dir%}
    Halconfig halconfig = halconfigParser.getHalconfig();
    removeHalconfigDirPrefix(halconfig);
    halconfigParser.saveConfig();
  }

  /**
   * Removes {@link
   * com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile#RELATIVE_PATH_PLACEHOLDER}
   * instances from a backup. This is held for backwards compatibility reading old backups, new
   * backups don't use the prefix and relative file paths are always resolved to hal config home.
   *
   * @param halconfig instance from backup.
   */
  @Deprecated
  private void removeHalconfigDirPrefix(Halconfig halconfig) {
    makeAbsoluteFilesRelative(halconfig, LocalFile.RELATIVE_PATH_PLACEHOLDER);
  }

  public String create() {
    String halconfigDir = directoryStructure.getHalconfigDirectory();
    halconfigParser.backupConfig();
    Halconfig halconfig = halconfigParser.getHalconfig();
    backupLocalFiles(halconfig, directoryStructure.getBackupConfigDependenciesPath().toString());
    makeAbsoluteFilesRelative(halconfig, halconfigDir);
    halconfigParser.saveConfig();

    SimpleDateFormat dateFormatter =
        new SimpleDateFormat("'halyard'-yyyy-MM-dd_HH-mm-ss-SSSXXX'.tar'");
    String tarOutputName = dateFormatter.format(new Date());

    String halconfigTar = Paths.get(System.getProperty("user.home"), tarOutputName).toString();
    try {
      tarHalconfig(halconfigDir, halconfigTar);
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Unable to safely backup halconfig " + e.getMessage(), e);
    } finally {
      halconfigParser.switchToBackupConfig();
      halconfigParser.getHalconfig();
      halconfigParser.saveConfig();
      halconfigParser.switchToPrimaryConfig();
    }

    return halconfigTar;
  }

  private void untarHalconfig(String halconfigDir, String halconfigTar) {
    FileInputStream tarInput = null;
    TarArchiveInputStream tarArchiveInputStream = null;

    try {
      tarInput = new FileInputStream(new File(halconfigTar));
      tarArchiveInputStream =
          (TarArchiveInputStream)
              new ArchiveStreamFactory().createArchiveInputStream("tar", tarInput);

    } catch (IOException | ArchiveException e) {
      throw new HalException(Problem.Severity.FATAL, "Failed to open backup: " + e.getMessage(), e);
    }

    try {
      ArchiveEntry archiveEntry = tarArchiveInputStream.getNextEntry();
      while (archiveEntry != null) {
        String entryName = archiveEntry.getName();
        Path outputPath = Paths.get(halconfigDir, entryName);
        File outputFile = outputPath.toFile();
        if (!outputFile.getParentFile().exists()) {
          outputFile.getParentFile().mkdirs();
        }

        if (archiveEntry.isDirectory()) {
          outputFile.mkdir();
        } else {
          Files.copy(tarArchiveInputStream, outputPath, REPLACE_EXISTING);
        }

        archiveEntry = tarArchiveInputStream.getNextEntry();
      }
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to read archive entry: " + e.getMessage(), e);
    }
  }

  private void tarHalconfig(String halconfigDir, String halconfigTar) throws IOException {
    FileOutputStream tarOutput = null;
    BufferedOutputStream bufferedTarOutput = null;
    TarArchiveOutputStream tarArchiveOutputStream = null;
    IOException fatalCleanup = null;
    try {
      tarOutput = new FileOutputStream(new File(halconfigTar));
      bufferedTarOutput = new BufferedOutputStream(tarOutput);
      tarArchiveOutputStream = new TarArchiveOutputStream(bufferedTarOutput);
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      TarArchiveOutputStream finalTarArchiveOutputStream = tarArchiveOutputStream;
      Arrays.stream(new File(halconfigDir).listFiles())
          .filter(Objects::nonNull)
          .forEach(f -> addFileToTar(finalTarArchiveOutputStream, f.getAbsolutePath(), ""));
    } catch (HalException e) {
      log.info("HalException caught during tar operation", e);
      throw e;
    } catch (IOException e) {
      log.info("IOException caught during tar operation", e);
      throw new HalException(
          Problem.Severity.FATAL, "Failed to backup halconfig: " + e.getMessage(), e);
    } finally {
      if (tarArchiveOutputStream != null) {
        try {
          tarArchiveOutputStream.finish();
          tarArchiveOutputStream.close();
        } catch (IOException e) {
          fatalCleanup = e;
        }
      }

      if (bufferedTarOutput != null) {
        bufferedTarOutput.close();
      }

      if (tarOutput != null) {
        tarOutput.close();
      }
    }

    if (fatalCleanup != null) {
      throw fatalCleanup;
    }
  }

  private void addFileToTar(
      TarArchiveOutputStream tarArchiveOutputStream, String path, String base) {
    File file = new File(path);
    String fileName = file.getName();

    if (Arrays.stream(omitPaths).anyMatch(s -> s.equals(fileName))) {
      return;
    }

    String tarEntryName = String.join("/", base, fileName);
    try {
      if (file.isFile()) {
        TarArchiveEntry tarEntry = new TarArchiveEntry(file, tarEntryName);
        tarArchiveOutputStream.putArchiveEntry(tarEntry);
        IOUtils.copy(new FileInputStream(file), tarArchiveOutputStream);
        tarArchiveOutputStream.closeArchiveEntry();
      } else if (file.isDirectory()) {
        Arrays.stream(file.listFiles())
            .filter(Objects::nonNull)
            .forEach(f -> addFileToTar(tarArchiveOutputStream, f.getAbsolutePath(), tarEntryName));
      } else {
        log.warn("Unknown file type: " + file + " - skipping addition to tar archive");
      }
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL,
          "Unable to file "
              + file.getName()
              + " to archive entry: "
              + tarEntryName
              + " "
              + e.getMessage(),
          e);
    }
  }

  public List<String> backupLocalFiles(Node node, String outputPath) {
    List<String> files = new ArrayList<>();

    Consumer<Node> fileFinder =
        n ->
            files.addAll(
                n.localFiles().stream()
                    .map(
                        f -> {
                          try {
                            Path fPath = fileService.getLocalFilePath(n.getStringFieldValue(f));
                            if (fPath == null) {
                              return null;
                            }
                            File fFile = fPath.toFile();
                            String fName = fFile.getName().replaceAll("[^-._a-zA-Z0-9]", "-");

                            // Hash the path to uniquely flatten all files into the output directory
                            Path newName =
                                Paths.get(outputPath, Math.abs(fPath.hashCode()) + "-" + fName);
                            File parent = newName.toFile().getParentFile();
                            if (!parent.exists()) {
                              parent.mkdirs();
                            } else if (fFile.getParent() != null
                                && fFile.getParent().equals(parent.toString())) {
                              // Don't move paths that are already in the right folder
                              return fPath.toString();
                            }
                            Files.copy(fPath, newName, REPLACE_EXISTING);

                            n.setStringFieldValue(f, newName.toString());
                            return newName.toString();
                          } catch (IOException e) {
                            throw new HalException(
                                FATAL, "Failed to backup user file: " + e.getMessage(), e);
                          }
                        })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
    node.recursiveConsume(fileFinder);

    return files;
  }

  /**
   * Changes all file paths of Halconfig and beginning with "root" from being absolute, to a
   * relative path by removing "root".
   *
   * @param halconfig instance to transform.
   * @param root prefix to remove from the local path.
   */
  private void makeAbsoluteFilesRelative(Halconfig halconfig, String root) {
    halconfig.recursiveConsume(
        n ->
            n.localFiles()
                .forEach(
                    field -> {
                      String fPath = n.getStringFieldValue(field);
                      if (StringUtils.isEmpty(fPath)) {
                        return;
                      }
                      Path localPath = Paths.get(fPath);
                      if (localPath.isAbsolute() || localPath.startsWith(root)) {
                        Path rootPath = Paths.get(root);
                        Path relativePath = rootPath.relativize(localPath);
                        n.setStringFieldValue(field, relativePath.toString());
                      }
                    }));
  }
}
