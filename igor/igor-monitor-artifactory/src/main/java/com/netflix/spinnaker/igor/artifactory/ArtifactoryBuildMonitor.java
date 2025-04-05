/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.artifactory;

import static java.util.Collections.emptyList;
import static org.jfrog.artifactory.client.aql.AqlItem.aqlItem;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.artifactory.model.ArtifactoryItem;
import com.netflix.spinnaker.igor.artifactory.model.ArtifactoryRepositoryType;
import com.netflix.spinnaker.igor.artifactory.model.ArtifactorySearch;
import com.netflix.spinnaker.igor.config.ArtifactoryProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.ArtifactoryEvent;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.DeltaItem;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.polling.PollingDelta;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.aql.AqlItem;
import org.jfrog.artifactory.client.aql.AqlQueryBuilder;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("artifactory.enabled")
@Slf4j
public class ArtifactoryBuildMonitor
    extends CommonPollingMonitor<
        ArtifactoryBuildMonitor.ArtifactDelta, ArtifactoryBuildMonitor.ArtifactPollingDelta> {
  private final ArtifactoryCache cache;
  private final ArtifactoryProperties artifactoryProperties;
  private final Optional<EchoService> echoService;

  public ArtifactoryBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      Optional<EchoService> echoService,
      ArtifactoryCache cache,
      ArtifactoryProperties artifactoryProperties,
      TaskScheduler taskScheduler) {
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        taskScheduler);
    this.cache = cache;
    this.artifactoryProperties = artifactoryProperties;
    this.echoService = echoService;
  }

  @Override
  public String getName() {
    return "artifactoryPublishingMonitor";
  }

  @Override
  public void poll(boolean sendEvents) {
    for (ArtifactorySearch search : artifactoryProperties.getSearches()) {
      pollSingle(new PollContext(search.getPartitionName(), !sendEvents));
    }
  }

  @Override
  protected ArtifactPollingDelta generateDelta(PollContext ctx) {
    return artifactoryProperties.getSearches().stream()
        .filter(host -> host.getPartitionName().equals(ctx.partitionName))
        .findAny()
        .map(
            search -> {
              Artifactory client =
                  ArtifactoryClientBuilder.create()
                      .setUsername(search.getUsername())
                      .setPassword(search.getPassword())
                      .setAccessToken(search.getAccessToken())
                      .setUrl(search.getBaseUrl())
                      .setIgnoreSSLIssues(search.isIgnoreSslIssues())
                      .build();

              int lookBackWindowMins =
                  igorProperties.getSpinnaker().getBuild().getLookBackWindowMins();
              long lookbackFromCurrent =
                  System.currentTimeMillis()
                      - (getPollInterval() * 1000 + (lookBackWindowMins * 60 * 1000));
              AqlItem modified = aqlItem("$last", lookBackWindowMins + "minutes");

              Long cursor = cache.getLastPollCycleTimestamp(search);
              if (cursor == null) {
                if (!igorProperties.getSpinnaker().getBuild().isHandleFirstBuilds()) {
                  return ArtifactPollingDelta.EMPTY;
                }
              } else if (cursor > lookbackFromCurrent
                  || igorProperties
                      .getSpinnaker()
                      .getBuild()
                      .isProcessBuildsOlderThanLookBackWindow()) {
                modified = aqlItem("$gt", Instant.ofEpochMilli(cursor).toString());
              }
              cache.setLastPollCycleTimestamp(search, System.currentTimeMillis());

              String pathMatch =
                  search.getGroupId() == null ? "" : search.getGroupId().replace('.', '/') + "/";

              List<String> includes =
                  new ArrayList<>(Arrays.asList("path", "repo", "name", "artifact.module.build"));
              if (ArtifactoryRepositoryType.HELM.equals(search.getRepoType())) {
                includes.add("@chart.name");
                includes.add("@chart.version");
              }

              AqlQueryBuilder aqlQueryBuilder =
                  new AqlQueryBuilder()
                      .item(aqlItem("repo", search.getRepo()))
                      .item(aqlItem("modified", modified))
                      .item(aqlItem("path", aqlItem("$match", pathMatch + "*")))
                      .item(aqlItem("name", aqlItem("$match", "*" + search.getArtifactExtension())))
                      .include(includes.toArray(new String[0]));

              ArtifactoryRequest aqlRequest =
                  new ArtifactoryRequestImpl()
                      .method(ArtifactoryRequest.Method.POST)
                      .apiUrl("api/search/aql")
                      .requestType(ArtifactoryRequest.ContentType.TEXT)
                      .responseType(ArtifactoryRequest.ContentType.JSON)
                      .requestBody(aqlQueryBuilder.build());

              try {
                ArtifactoryResponse aqlResponse = client.restCall(aqlRequest);
                if (aqlResponse.isSuccessResponse()) {
                  List<ArtifactoryItem> results =
                      aqlResponse.parseBody(ArtifactoryQueryResults.class).getResults();
                  return new ArtifactPollingDelta(
                      search.getName(),
                      search.getBaseUrl(),
                      search.getPartitionName(),
                      Collections.singletonList(
                          new ArtifactDelta(
                              System.currentTimeMillis(), search.getRepoType(), results)));
                }

                log.warn(
                    "Unable to query Artifactory for artifacts (HTTP {}): {}",
                    aqlResponse.getStatusLine().getStatusCode(),
                    aqlResponse.getRawBody());
              } catch (IOException e) {
                log.warn("Unable to query Artifactory for artifacts", e);
              }
              return ArtifactPollingDelta.EMPTY;
            })
        .orElse(ArtifactPollingDelta.EMPTY);
  }

  @Override
  protected void commitDelta(ArtifactPollingDelta delta, boolean sendEvents) {
    for (ArtifactDelta artifactDelta : delta.items) {
      if (sendEvents) {
        for (ArtifactoryItem artifact : artifactDelta.getArtifacts()) {
          Artifact matchableArtifact =
              artifact.toMatchableArtifact(artifactDelta.getType(), delta.getBaseUrl());
          postEvent(matchableArtifact, delta.getName());
          log.debug("{} event posted", artifact);
        }
      }
    }
  }

  private void postEvent(Artifact artifact, String name) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send build notification: Echo is not configured");
      registry.counter(missedNotificationId.withTag("monitor", getName())).increment();
    } else {
      if (artifact != null) {
        AuthenticatedRequest.allowAnonymous(
            () ->
                Retrofit2SyncCall.execute(
                    echoService
                        .get()
                        .postEvent(
                            new ArtifactoryEvent(new ArtifactoryEvent.Content(name, artifact)))));
      }
    }
  }

  @Data
  static class ArtifactPollingDelta implements PollingDelta<ArtifactDelta> {
    public static ArtifactPollingDelta EMPTY =
        new ArtifactPollingDelta(null, null, null, emptyList());

    private final String name;

    private final String baseUrl;

    @Nullable private final String repo;

    private final List<ArtifactDelta> items;
  }

  @Data
  static class ArtifactDelta implements DeltaItem {
    private final long searchTimestamp;
    private final ArtifactoryRepositoryType type;
    private final List<ArtifactoryItem> artifacts;
  }

  @Data
  private static class ArtifactoryQueryResults {
    List<ArtifactoryItem> results;
  }
}
