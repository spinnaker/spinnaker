/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.igor;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import okhttp3.ResponseBody;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.util.UriUtils;
import retrofit2.Response;

@RequiredArgsConstructor
@EnableConfigurationProperties(IgorFeatureFlagProperties.class)
public class BuildService {
  private final IgorService igorService;
  private final IgorFeatureFlagProperties igorFeatureFlagProperties;

  private String encode(String uri) {
    return UriUtils.encodeFragment(uri, "UTF-8");
  }

  public Response<ResponseBody> build(
      String master, String jobName, Map<String, String> queryParams) {
    return Retrofit2SyncCall.executeCall(
        igorService.build(master, encode(jobName), queryParams, ""));
  }

  public Response<ResponseBody> build(
      String master, String jobName, Map<String, String> queryParams, String startTime) {
    return Retrofit2SyncCall.executeCall(
        igorService.build(master, encode(jobName), queryParams, startTime));
  }

  public String stop(String master, String jobName, String queuedBuild, Long buildNumber) {
    return this.igorFeatureFlagProperties.isJobNameAsQueryParameter()
        ? Retrofit2SyncCall.execute(
            igorService.stopWithJobNameAsQueryParameter(
                master, jobName, queuedBuild, buildNumber, ""))
        : Retrofit2SyncCall.execute(
            igorService.stop(master, jobName, queuedBuild, buildNumber, ""));
  }

  public Map queuedBuild(String master, String item) {
    return Retrofit2SyncCall.execute(igorService.queuedBuild(master, item));
  }

  public Map<String, Object> getBuild(Long buildNumber, String master, String job) {
    return this.igorFeatureFlagProperties.isJobNameAsQueryParameter()
        ? Retrofit2SyncCall.execute(
            igorService.getBuildWithJobAsQueryParam(buildNumber, master, encode(job)))
        : Retrofit2SyncCall.execute(igorService.getBuild(buildNumber, master, encode(job)));
  }

  public Map<String, Object> getPropertyFile(
      Long buildNumber, String fileName, String master, String job) {
    return this.igorFeatureFlagProperties.isJobNameAsQueryParameter()
        ? Retrofit2SyncCall.execute(
            igorService.getPropertyFileWithJobAsQueryParam(
                buildNumber, fileName, master, encode(job)))
        : Retrofit2SyncCall.execute(
            igorService.getPropertyFile(buildNumber, fileName, master, encode(job)));
  }

  public List<Artifact> getArtifacts(Long buildNumber, String fileName, String master, String job) {
    return this.igorFeatureFlagProperties.isJobNameAsQueryParameter()
        ? Retrofit2SyncCall.execute(
            igorService.getArtifactsWithJobAsQueryParam(buildNumber, master, encode(job), fileName))
        : Retrofit2SyncCall.execute(
            igorService.getArtifacts(buildNumber, master, encode(job), fileName));
  }

  public Response<ResponseBody> updateBuild(
      String master, String jobName, Long buildNumber, IgorService.UpdatedBuild updatedBuild) {
    return Retrofit2SyncCall.executeCall(
        igorService.update(master, jobName, buildNumber, updatedBuild));
  }
}
