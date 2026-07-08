/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.travis;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.BuildCache;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Branch;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Commit;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Repository;
import com.netflix.spinnaker.igor.travis.config.TravisProperties;
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter;
import com.netflix.spinnaker.igor.travis.service.TravisService;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import retrofit2.mock.Calls;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravisBuildMonitorTest {
  @Mock BuildCache buildCache;
  @Mock TravisService travisService;
  @Mock EchoService echoService;
  @Mock TaskScheduler taskScheduler;
  TravisBuildMonitor travisBuildMonitor;

  String MASTER = "MASTER";
  int CACHED_JOB_TTL_SECONDS = 172800;
  int CACHED_JOB_TTL_DAYS = 2;

  @BeforeEach
  void setup() {
    TravisProperties travisProperties = new TravisProperties();
    travisProperties.setCachedJobTTLDays(CACHED_JOB_TTL_DAYS);
    BuildServices buildServices = new BuildServices();
    buildServices.addServices(Map.of(MASTER, travisService));
    travisBuildMonitor =
        new TravisBuildMonitor(
            new IgorConfigurationProperties(),
            new NoopRegistry(),
            new DynamicConfigService.NoopDynamicConfig(),
            new DiscoveryStatusListener(true),
            buildCache,
            buildServices,
            travisProperties,
            Optional.of(echoService),
            Optional.empty(),
            taskScheduler);
    when(travisService.isLogReady(any())).thenReturn(true);
    when(buildCache.getTrackedBuilds(MASTER)).thenReturn(List.of());
  }

  @Test
  void flagANewBuildOnMasterButDoNotSendEventOnRepoIfANewerBuildIsPresentAtRepoLevel() {
    V3Build build = mock(V3Build.class);
    V3Repository repository = mock(V3Repository.class);
    when(echoService.postEvent(any())).thenReturn(Calls.response(null));

    when(travisService.getLatestBuilds()).thenReturn(List.of(build));
    when(build.branchedRepoSlug()).thenReturn("test-org/test-repo/master");
    when(build.getJobs()).thenReturn(List.of());
    when(build.getNumber()).thenReturn(4);
    when(build.getState()).thenReturn(TravisBuildState.passed);
    when(build.getRepository()).thenReturn(repository);
    when(repository.getSlug()).thenReturn("test-org/test-repo");

    when(travisService.getGenericBuild(build, true))
        .thenReturn(TravisBuildConverter.genericBuild(build, MASTER));
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/master", false)).thenReturn(3);
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo", false)).thenReturn(5);

    TravisBuildMonitor.BuildPollingDelta buildPollingDelta =
        travisBuildMonitor.generateDelta(new PollContext(MASTER));
    travisBuildMonitor.commitDelta(buildPollingDelta, true);

    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo/master", 4, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, never())
        .setLastBuild(MASTER, "test-org/test-repo", 4, false, CACHED_JOB_TTL_SECONDS);

    assertEquals(1, buildPollingDelta.getItems().size());
    assertEquals("test-org/test-repo/master", buildPollingDelta.getItems().get(0).branchedRepoSlug);
    assertEquals(4, buildPollingDelta.getItems().get(0).currentBuildNum);
    assertEquals(3, buildPollingDelta.getItems().get(0).previousBuildNum);
  }

  @Test
  void sendEventsForBuildBothOnBranchAndOnRepository() {
    V3Build build = mock(V3Build.class);
    V3Repository repository = mock(V3Repository.class);

    when(travisService.getLatestBuilds()).thenReturn(List.of(build));
    when(build.branchedRepoSlug()).thenReturn("test-org/test-repo/my_branch");
    when(build.getNumber()).thenReturn(4);
    when(build.getState()).thenReturn(TravisBuildState.passed);
    when(build.getJobs()).thenReturn(List.of());
    when(build.getRepository()).thenReturn(repository);
    when(repository.getSlug()).thenReturn("test-org/test-repo");

    when(travisService.getGenericBuild(build, true))
        .thenReturn(TravisBuildConverter.genericBuild(build, MASTER));
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/my_branch", false)).thenReturn(3);

    TravisBuildMonitor.BuildPollingDelta buildPollingDelta =
        travisBuildMonitor.generateDelta(new PollContext(MASTER));
    travisBuildMonitor.commitDelta(buildPollingDelta, true);

    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo/my_branch", 4, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo", 4, false, CACHED_JOB_TTL_SECONDS);

    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo")
                        && event.getContent().getProject().getLastBuild().getNumber() == 4));
    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo/my_branch")
                        && event.getContent().getProject().getLastBuild().getNumber() == 4));
  }

  @Test
  void suppressEchoNotifications() {
    V3Build build = mock(V3Build.class);
    V3Repository repository = mock(V3Repository.class);

    when(travisService.getLatestBuilds()).thenReturn(List.of(build));
    when(build.branchedRepoSlug()).thenReturn("test-org/test-repo/my_branch");
    when(build.getNumber()).thenReturn(4);
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/my_branch", false)).thenReturn(3);
    when(build.getJobs()).thenReturn(List.of());
    when(build.getRepository()).thenReturn(repository);
    when(repository.getSlug()).thenReturn("test-org/test-repo");
    when(build.getState()).thenReturn(TravisBuildState.passed);

    TravisBuildMonitor.BuildPollingDelta buildPollingDelta =
        travisBuildMonitor.generateDelta(new PollContext(MASTER));
    travisBuildMonitor.commitDelta(buildPollingDelta, false);

    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo/my_branch", 4, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo", 4, false, CACHED_JOB_TTL_SECONDS);

    verify(echoService, never()).postEvent(any());
  }

  @Test
  void sendEventsWhenTwoDifferentBranchesBuildAtTheSameTime() {
    V3Build build = mock(V3Build.class);
    V3Build buildDifferentBranch = mock(V3Build.class);
    V3Repository repository = mock(V3Repository.class);

    when(travisService.getLatestBuilds()).thenReturn(List.of(build, buildDifferentBranch));
    when(build.branchedRepoSlug()).thenReturn("test-org/test-repo/my_branch");
    when(build.getNumber()).thenReturn(4);
    when(build.getState()).thenReturn(TravisBuildState.passed);
    when(build.getJobs()).thenReturn(List.of());
    when(build.getRepository()).thenReturn(repository);
    when(buildDifferentBranch.branchedRepoSlug()).thenReturn("test-org/test-repo/different_branch");
    when(buildDifferentBranch.getNumber()).thenReturn(3);
    when(buildDifferentBranch.getState()).thenReturn(TravisBuildState.passed);
    when(buildDifferentBranch.getJobs()).thenReturn(List.of());
    when(buildDifferentBranch.getRepository()).thenReturn(repository);
    when(repository.getSlug()).thenReturn("test-org/test-repo");
    when(travisService.getGenericBuild(build, true))
        .thenReturn(TravisBuildConverter.genericBuild(build, MASTER));
    when(travisService.getGenericBuild(buildDifferentBranch, true))
        .thenReturn(TravisBuildConverter.genericBuild(buildDifferentBranch, MASTER));
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/my_branch", false)).thenReturn(2);
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/different_branch", false))
        .thenReturn(1);

    TravisBuildMonitor.BuildPollingDelta buildPollingDelta =
        travisBuildMonitor.generateDelta(new PollContext(MASTER));
    travisBuildMonitor.commitDelta(buildPollingDelta, true);

    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo/my_branch", 4, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo", 4, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo", 3, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, times(1))
        .setLastBuild(
            MASTER, "test-org/test-repo/different_branch", 3, false, CACHED_JOB_TTL_SECONDS);

    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo/my_branch")
                        && event.getContent().getProject().getLastBuild().getNumber() == 4));
    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo")
                        && event.getContent().getProject().getLastBuild().getNumber() == 4));
    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo")
                        && event.getContent().getProject().getLastBuild().getNumber() == 3));
    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event
                            .getContent()
                            .getProject()
                            .getName()
                            .equals("test-org/test-repo/different_branch")
                        && event.getContent().getProject().getLastBuild().getNumber() == 3));
  }

  @Test
  void shouldKeepTrackOfStartedBuildsAndMonitorThemEvenIfTheyDisappearFromTheTravisAPI() {
    V3Build build = new V3Build();
    V3Repository repository = mock(V3Repository.class);

    V3Commit commit = mock(V3Commit.class);
    when(commit.isTag()).thenReturn(false);
    when(commit.isPullRequest()).thenReturn(false);
    build.setCommit(commit);
    V3Branch branch = new V3Branch();
    branch.setName("my_branch");
    build.setBranch(branch);
    build.setId(1337);
    build.setNumber(4);
    build.setState(TravisBuildState.started);
    build.setJobs(List.of());
    build.setRepository(repository);
    when(repository.getSlug()).thenReturn("test-org/test-repo");

    when(travisService.getLatestBuilds()).thenReturn(List.of(build)).thenReturn(List.of());
    when(buildCache.getTrackedBuilds(MASTER))
        .thenReturn(List.of(Map.of("buildId", "1337")));
    when(travisService.getV3Build(1337)).thenReturn(build);
    when(travisService.getGenericBuild(any(V3Build.class), anyBoolean()))
        .thenAnswer(
            invocation -> {
              V3Build b = invocation.getArgument(0);
              return TravisBuildConverter.genericBuild(b, MASTER);
            });
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/my_branch", true)).thenReturn(3);
    when(buildCache.getLastBuild(MASTER, "test-org/test-repo/my_branch", false)).thenReturn(3);

    TravisBuildMonitor.BuildPollingDelta buildPollingDelta =
        travisBuildMonitor.generateDelta(new PollContext(MASTER));
    travisBuildMonitor.commitDelta(buildPollingDelta, true);

    build.setState(TravisBuildState.passed);
    buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER));
    travisBuildMonitor.commitDelta(buildPollingDelta, true);

    verify(buildCache, times(1))
        .setTracking(MASTER, build.getRepository().getSlug(), 1337, TravisBuildMonitor.TRACKING_TTL_SECS);
    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo/my_branch", 4, false, CACHED_JOB_TTL_SECONDS);
    verify(buildCache, times(1))
        .setLastBuild(MASTER, "test-org/test-repo", 4, false, CACHED_JOB_TTL_SECONDS);

    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo")
                        && event.getContent().getProject().getLastBuild().getNumber() == 4));
    verify(echoService, times(1))
        .postEvent(
            ArgumentMatchers.argThat(
                event ->
                    event.getContent().getProject().getName().equals("test-org/test-repo/my_branch")
                        && event.getContent().getProject().getLastBuild().getNumber() == 4));
  }
}
