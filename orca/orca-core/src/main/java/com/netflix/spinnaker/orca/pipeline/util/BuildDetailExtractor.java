/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.orca.pipeline.util;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.BuildInfo;
import com.netflix.spinnaker.orca.pipeline.model.JenkinsBuildInfo;
import com.netflix.spinnaker.orca.pipeline.model.SourceControl;
import java.util.*;
import org.apache.commons.lang3.StringUtils;

public class BuildDetailExtractor {

  private final List<DetailExtractor> detailExtractors;
  private final ObjectMapper mapper = OrcaObjectMapper.getInstance();

  public BuildDetailExtractor() {
    this.detailExtractors =
        Arrays.asList(new DefaultDetailExtractor(), new LegacyJenkinsUrlDetailExtractor());
  }

  public boolean tryToExtractBuildDetails(BuildInfo<?> buildInfo, Map<String, Object> request) {
    // The first strategy to succeed ends the loop. That is: the DefaultDetailExtractor is trying
    // first
    // if it can not succeed the Legacy parser will be applied
    return detailExtractors.stream()
        .anyMatch(it -> it.tryToExtractBuildDetails(buildInfo, request));
  }

  @Deprecated
  public boolean tryToExtractJenkinsBuildDetails(
      Map<String, Object> buildInfo, Map<String, Object> request) {
    try {
      return tryToExtractBuildDetails(
          mapper.convertValue(buildInfo, JenkinsBuildInfo.class), request);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  // Legacy Details extractor for Jenkins. It parses the url to fill the request build parameters
  @Deprecated
  private static class LegacyJenkinsUrlDetailExtractor implements DetailExtractor {

    @Override
    public boolean tryToExtractBuildDetails(BuildInfo<?> buildInfo, Map<String, Object> request) {
      if (buildInfo == null || request == null) {
        return false;
      }
      Map<String, Object> copyRequest = new HashMap<>();
      List<String> buildInfoUrlParts;
      String buildInfoUrl = buildInfo.getUrl();
      if (buildInfoUrl != null) {
        buildInfoUrlParts = parseBuildInfoUrl(buildInfoUrl);
        if (buildInfoUrlParts.size() == 3) {
          copyRequest.put("buildInfoUrl", buildInfoUrl);
          copyRequest.put("buildHost", buildInfoUrlParts.get(0));
          copyRequest.put("job", buildInfoUrlParts.get(1));
          copyRequest.put("buildNumber", buildInfoUrlParts.get(2));
          extractCommitHash(buildInfo, copyRequest);
          request.putAll(copyRequest);
          return true;
        }
      }
      return false;
    }

    // Naming-convention for buildInfo.url is $protocol://$buildHost/job/$job/$buildNumber/.
    // For example: http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/
    // Note that job names can contain slashes if using the Folders plugin.
    // For example: http://spinnaker.builds.test.netflix.net/job/folder1/job/job1/69/
    private List<String> parseBuildInfoUrl(String url) {
      List<String> urlParts = new ArrayList<>();
      urlParts.addAll(Arrays.asList(url.split("/+")));
      if (urlParts.size() >= 5) {
        String buildNumber = urlParts.remove(urlParts.size() - 1);
        String job = urlParts.subList(3, urlParts.size()).stream().collect(joining("/"));

        String buildHost = format("%s//%s/", urlParts.get(0), urlParts.get(1));

        return Arrays.asList(buildHost, job, buildNumber);
      }
      return emptyList();
    }
  }

  // Default detail extractor. It expects to find url, name and number in the buildInfo
  private static class DefaultDetailExtractor implements DetailExtractor {

    @Override
    public boolean tryToExtractBuildDetails(BuildInfo<?> buildInfo, Map<String, Object> request) {

      if (buildInfo == null || request == null) {
        return false;
      }
      if (buildInfo.getUrl() != null && buildInfo.getName() != null && buildInfo.getNumber() > 0) {
        Map<String, Object> copyRequest = new HashMap<>();
        copyRequest.put("buildInfoUrl", buildInfo.getUrl());
        copyRequest.put("job", buildInfo.getName());
        copyRequest.put("buildNumber", buildInfo.getNumber());
        extractBuildHost(buildInfo.getUrl(), copyRequest);
        extractCommitHash(buildInfo, copyRequest);
        request.putAll(copyRequest);
        return true;
      }
      return false;
    }

    private void extractBuildHost(String url, Map<String, Object> request) {
      List<String> urlParts = Arrays.asList(url.split("/+"));
      if (urlParts.size() >= 5) {
        request.put("buildHost", format("%s//%s/", urlParts.get(0), urlParts.get(1)));
      }
    }
  }

  // Common trait for DetailExtractor
  private interface DetailExtractor {

    boolean tryToExtractBuildDetails(BuildInfo<?> buildInfo, Map<String, Object> request);

    default void extractCommitHash(BuildInfo<?> buildInfo, Map<String, Object> request) {
      // buildInfo.scm contains a list of maps. Each map contains these keys: name, sha1, branch.
      // If the list contains more than one entry, prefer the first one that is not master and is
      // not develop.
      String commitHash = null;

      if (buildInfo.getScm() != null && buildInfo.getScm().size() >= 2) {
        commitHash =
            buildInfo.getScm().stream()
                .filter(it -> !"master".equals(it.getBranch()) && !"develop".equals(it.getBranch()))
                .findFirst()
                .map(SourceControl::getSha1)
                .orElse(null);
      }
      if (StringUtils.isEmpty(commitHash)
          && buildInfo.getScm() != null
          && !buildInfo.getScm().isEmpty()) {
        commitHash = buildInfo.getScm().get(0).getSha1();
      }
      if (commitHash != null) {
        request.put("commitHash", commitHash);
      }
    }
  }
}
