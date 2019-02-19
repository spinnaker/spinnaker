/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.util.AbstractMessageLogger;
import org.apache.ivy.util.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
public class IvyArtifactCredentials implements ArtifactCredentials {
  @Getter
  private final List<String> types = Collections.singletonList("ivy/file");
  private final IvyArtifactAccount account;
  private final Supplier<Path> cacheBuilder;

  public IvyArtifactCredentials(IvyArtifactAccount account) {
    this(account, () -> Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString()));
  }

  public IvyArtifactCredentials(IvyArtifactAccount account, Supplier<Path> cacheBuilder) {
    this.cacheBuilder = cacheBuilder;
    redirectIvyLogsToSlf4j();
    this.account = account;
  }

  private static void redirectIvyLogsToSlf4j() {
    Message.setDefaultLogger(new AbstractMessageLogger() {
      private final Logger logger = LoggerFactory.getLogger("org.apache.ivy");

      @Override
      protected void doProgress() {
      }

      @Override
      protected void doEndProgress(String msg) {
        log(msg, Message.MSG_INFO);
      }

      @Override
      public void log(String msg, int level) {
        switch (level) {
          case Message.MSG_ERR:
            logger.error(msg);
            break;
          case Message.MSG_WARN:
            logger.warn(msg);
            break;
          case Message.MSG_INFO:
            logger.info(msg);
            break;
          case Message.MSG_DEBUG:
            logger.debug(msg);
            break;
          case Message.MSG_VERBOSE:
            logger.trace(msg);
          default:
            // do nothing
        }
      }

      @Override
      public void rawlog(String msg, int level) {
        log(msg, level);
      }
    });
  }

  public InputStream download(Artifact artifact) {
    Path cacheDir = cacheBuilder.get();
    Ivy ivy = account.getSettings().toIvy(cacheDir);

    String[] parts = artifact.getReference().split(":");
    if (parts.length < 3) {
      throw new IllegalArgumentException("Ivy artifact reference must have a group, artifact, and version separated by ':'");
    }

    ModuleRevisionId mrid = new ModuleRevisionId(new ModuleId(parts[0], parts[1]), parts[2]);

    try {
      ResolveReport report = ivy.resolve(mrid, (ResolveOptions) new ResolveOptions()
        .setTransitive(false)
        .setConfs(account.getResolveConfigurations().toArray(new String[0]))
        .setLog("download-only"), true);
      return Arrays.stream(report.getAllArtifactsReports())
        .findFirst()
        .map(rep -> {
          try {
            return new DiskFreeingInputStream(new FileInputStream(rep.getLocalFile()), cacheDir);
          } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
          }
        })
        .orElseThrow(() -> new IllegalArgumentException("Unable to resolve artifact for reference '" + artifact.getReference() + "'"));
    } catch (ParseException | IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public String getName() {
    return account.getName();
  }
}
