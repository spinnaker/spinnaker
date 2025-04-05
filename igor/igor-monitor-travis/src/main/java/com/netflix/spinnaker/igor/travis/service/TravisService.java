/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.service;

import static java.util.Collections.emptyList;
import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.build.model.GenericJobConfiguration;
import com.netflix.spinnaker.igor.build.model.JobConfiguration;
import com.netflix.spinnaker.igor.build.model.Result;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.ArtifactDecorator;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildProperties;
import com.netflix.spinnaker.igor.travis.TravisCache;
import com.netflix.spinnaker.igor.travis.client.TravisClient;
import com.netflix.spinnaker.igor.travis.client.logparser.ArtifactParser;
import com.netflix.spinnaker.igor.travis.client.logparser.PropertyParser;
import com.netflix.spinnaker.igor.travis.client.model.AccessToken;
import com.netflix.spinnaker.igor.travis.client.model.Build;
import com.netflix.spinnaker.igor.travis.client.model.Builds;
import com.netflix.spinnaker.igor.travis.client.model.Commit;
import com.netflix.spinnaker.igor.travis.client.model.EmptyObject;
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth;
import com.netflix.spinnaker.igor.travis.client.model.v3.Config;
import com.netflix.spinnaker.igor.travis.client.model.v3.RepoRequest;
import com.netflix.spinnaker.igor.travis.client.model.v3.Request;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildType;
import com.netflix.spinnaker.igor.travis.client.model.v3.TriggerResponse;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Job;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.SupplierUtils;
import io.vavr.control.Either;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.logstash.logback.argument.StructuredArguments;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TravisService implements BuildOperations, BuildProperties {

  static final int TRAVIS_JOB_RESULT_LIMIT = 100;

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String baseUrl;
  private final String groupKey;
  private final GithubAuth gitHubAuth;
  private final int numberOfJobs;
  private final int buildResultLimit;
  private Collection<String> filteredRepositories;
  private final TravisClient travisClient;
  private final TravisCache travisCache;
  private final Collection<String> artifactRegexes;
  private final Optional<ArtifactDecorator> artifactDecorator;
  private final String buildMessageKey;
  private final Permissions permissions;
  private final boolean legacyLogFetching;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  protected AccessToken accessToken;

  public TravisService(
      String travisHostId,
      String baseUrl,
      String githubToken,
      int numberOfJobs,
      int buildResultLimit,
      Collection<String> filteredRepositories,
      TravisClient travisClient,
      TravisCache travisCache,
      Optional<ArtifactDecorator> artifactDecorator,
      Collection<String> artifactRegexes,
      String buildMessageKey,
      Permissions permissions,
      boolean legacyLogFetching,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.numberOfJobs = numberOfJobs;
    this.buildResultLimit = buildResultLimit;
    this.groupKey = travisHostId;
    this.gitHubAuth = new GithubAuth(githubToken);
    this.filteredRepositories = filteredRepositories;
    this.travisClient = travisClient;
    this.baseUrl = baseUrl;
    this.travisCache = travisCache;
    this.artifactDecorator = artifactDecorator;
    this.artifactRegexes =
        artifactRegexes != null ? new HashSet<>(artifactRegexes) : Collections.emptySet();
    this.buildMessageKey = buildMessageKey;
    this.permissions = permissions;
    this.legacyLogFetching = legacyLogFetching;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
  }

  @Override
  public String getName() {
    return this.groupKey;
  }

  @Override
  public BuildServiceProvider getBuildServiceProvider() {
    return BuildServiceProvider.TRAVIS;
  }

  @Override
  public List<GenericGitRevision> getGenericGitRevisions(String inputRepoSlug, GenericBuild build) {
    String repoSlug = cleanRepoSlug(inputRepoSlug);
    if (StringUtils.isNumeric(build.getId())) {
      V3Build v3build = getV3Build(Long.parseLong(build.getId()));
      if (v3build.getCommit() != null) {
        return Collections.singletonList(
            v3build
                .getCommit()
                .getGenericGitRevision()
                .withName(v3build.getBranch().getName())
                .withBranch(v3build.getBranch().getName()));
      }
    } else {
      log.info(
          "Getting getGenericGitRevisions for build {}:{} using deprecated V2 API",
          inputRepoSlug,
          build.getNumber());
      Builds builds = getBuilds(repoSlug, build.getNumber());
      final List<Commit> commits = builds.getCommits();
      if (commits != null) {
        return commits.stream()
            .filter(commit -> commit.getBranch() != null)
            .map(Commit::getGenericGitRevision)
            .collect(Collectors.toList());
      }
    }
    return null;
  }

  @Override
  public GenericBuild getGenericBuild(final String inputRepoSlug, final long buildNumber) {
    String repoSlug = cleanRepoSlug(inputRepoSlug);
    Build build = getBuild(repoSlug, buildNumber);
    return getGenericBuild(build, repoSlug);
  }

  @Override
  public long triggerBuildWithParameters(
      String inputRepoSlug, Map<String, String> queryParameters) {
    String repoSlug = cleanRepoSlug(inputRepoSlug);
    String branch = branchFromRepoSlug(inputRepoSlug);
    RepoRequest repoRequest = new RepoRequest(branch.isEmpty() ? "master" : branch);
    if (buildMessageKey != null && queryParameters.containsKey(buildMessageKey)) {
      String buildMessage = queryParameters.get(buildMessageKey);
      queryParameters.remove(buildMessageKey);
      repoRequest.setMessage(repoRequest.getMessage() + ": " + buildMessage);
    }
    repoRequest.setConfig(new Config(queryParameters));
    final TriggerResponse triggerResponse =
        Retrofit2SyncCall.execute(
            travisClient.triggerBuild(getAccessToken(), repoSlug, repoRequest));
    if (triggerResponse.getRemainingRequests() > 0) {
      log.debug(
          "{}: remaining requests: {}",
          StructuredArguments.kv("group", groupKey),
          triggerResponse.getRemainingRequests());
      log.info(
          "{}: Triggered build of {}, requestId: {}",
          StructuredArguments.kv("group", groupKey),
          inputRepoSlug,
          triggerResponse.getRequest().getId());
    }

    return travisCache.setQueuedJob(
        groupKey,
        triggerResponse.getRequest().getRepository().getId(),
        triggerResponse.getRequest().getId());
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  public V3Build getV3Build(long buildId) {
    return Retrofit2SyncCall.execute(
        travisClient.v3build(
            getAccessToken(), buildId, addLogCompleteIfApplicable("build.commit")));
  }

  public Builds getBuilds(String repoSlug, long buildNumber) {
    return Retrofit2SyncCall.execute(travisClient.builds(getAccessToken(), repoSlug, buildNumber));
  }

  public Build getBuild(String repoSlug, long buildNumber) {
    Builds builds = getBuilds(repoSlug, buildNumber);
    return !builds.getBuilds().isEmpty() ? builds.getBuilds().get(0) : null;
  }

  @Override
  public Map<String, Object> getBuildProperties(
      String inputRepoSlug, GenericBuild build, String fileName) {
    try {
      V3Build v3build = getV3Build(Long.parseLong(build.getId()));
      return PropertyParser.extractPropertiesFromLog(getLog(v3build));
    } catch (Exception e) {
      log.error("Unable to get igorProperties '{}'", kv("job", inputRepoSlug), e);
      return Collections.emptyMap();
    }
  }

  public List<GenericBuild> getTagBuilds(String repoSlug) {
    // Tags are hard to identify, no filters exist.
    // Increasing the limit to increase the odds for finding some tag builds.
    V3Builds builds =
        Retrofit2SyncCall.execute(
            travisClient.v3buildsByEventType(
                getAccessToken(),
                repoSlug,
                "push",
                buildResultLimit * 2,
                addLogCompleteIfApplicable()));
    return builds.getBuilds().stream()
        .filter(build -> build.getCommit().isTag())
        .filter(this::isLogReady)
        .map(this::getGenericBuild)
        .collect(Collectors.toList());
  }

  @Override
  public List<GenericBuild> getBuilds(String inputRepoSlug) {
    String repoSlug = cleanRepoSlug(inputRepoSlug);
    String branch = branchFromRepoSlug(inputRepoSlug);
    TravisBuildType travisBuildType = travisBuildTypeFromRepoSlug(inputRepoSlug);
    V3Builds builds;

    switch (travisBuildType) {
      case tag:
        return getTagBuilds(repoSlug);
      case branch:
        builds =
            Retrofit2SyncCall.execute(
                travisClient.v3builds(
                    getAccessToken(),
                    repoSlug,
                    branch,
                    "push,api",
                    buildResultLimit,
                    addLogCompleteIfApplicable()));
        break;
      case pull_request:
        builds =
            Retrofit2SyncCall.execute(
                travisClient.v3builds(
                    getAccessToken(),
                    repoSlug,
                    branch,
                    "pull_request",
                    buildResultLimit,
                    addLogCompleteIfApplicable()));
        break;
      case unknown:
      default:
        builds =
            Retrofit2SyncCall.execute(
                travisClient.v3builds(
                    getAccessToken(), repoSlug, buildResultLimit, addLogCompleteIfApplicable()));
    }

    return builds.getBuilds().stream()
        .filter(this::isLogReady)
        .map(this::getGenericBuild)
        .collect(Collectors.toList());
  }

  private List<V3Job> getJobs(int limit, TravisBuildState... buildStatesFilter) {
    CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("travis");

    Supplier<List<V3Job>> supplier =
        SupplierUtils.recover(
            () ->
                IntStream.rangeClosed(1, calculatePagination(limit))
                    .mapToObj(
                        page ->
                            Retrofit2SyncCall.execute(
                                travisClient.jobs(
                                    getAccessToken(),
                                    Arrays.stream(buildStatesFilter)
                                        .map(TravisBuildState::toString)
                                        .collect(Collectors.joining(",")),
                                    addLogCompleteIfApplicable("job.build"),
                                    getLimit(page, limit),
                                    (page - 1) * TRAVIS_JOB_RESULT_LIMIT)))
                    .flatMap(v3jobs -> v3jobs.getJobs().stream())
                    .sorted(Comparator.comparing(V3Job::getId))
                    .collect(Collectors.toList()),
            error -> {
              log.warn("An error occurred while fetching new jobs from Travis.", error);
              return emptyList();
            });

    return breaker.executeSupplier(supplier);
  }

  public List<V3Build> getLatestBuilds() {

    if (!filteredRepositories.isEmpty()) {
      return getBuildsForSpecificRepos(filteredRepositories);
    }

    Map<V3Build, List<V3Job>> jobs =
        getJobs(
                numberOfJobs,
                TravisBuildState.passed,
                TravisBuildState.started,
                TravisBuildState.errored,
                TravisBuildState.failed,
                TravisBuildState.canceled)
            .stream()
            .filter(job -> job.getBuild() != null)
            .collect(
                Collectors.groupingBy(V3Job::getBuild, LinkedHashMap::new, Collectors.toList()));

    return jobs.entrySet().stream()
        .map(
            entry -> {
              V3Build build = entry.getKey();
              build.setJobs(entry.getValue());
              return build;
            })
        .collect(Collectors.toList());
  }

  private List<V3Build> getBuildsForSpecificRepos(Collection<String> repositories) {
    return repositories.parallelStream()
        .map(this::fetchBuilds)
        .filter(Either::isLeft)
        .map(Either::getLeft)
        .flatMap(item -> item.getBuilds().stream())
        .collect(Collectors.toList());
  }

  private Either<V3Builds, Exception> fetchBuilds(String repoSlug) {
    try {
      log.info("fetching builds for repo: {}", repoSlug);
      return Either.left(
          Retrofit2SyncCall.execute(
              travisClient.v3builds(
                  getAccessToken(), repoSlug, buildResultLimit, addLogCompleteIfApplicable())));
    } catch (Exception e) {
      log.error("Failed to fetch builds for repo {}. Skipping. {}.", repoSlug, e.getMessage());
      return Either.right(e);
    }
  }

  private String addLogCompleteIfApplicable(String... initialParams) {
    List<String> queryParams = new ArrayList<>(Arrays.asList(initialParams));
    if (!legacyLogFetching) {
      queryParams.add("build.log_complete");
    }
    return StringUtils.stripToNull(String.join(",", queryParams));
  }

  public String getLog(Build build) {
    List<Integer> jobIds = build.getJob_ids();
    if (jobIds == null) {
      return "";
    }
    String travisLog =
        jobIds.stream()
            .map(this::getJobLog)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    log.info(
        "fetched logs for [buildNumber:{}], [buildId:{}], [logLength:{}]",
        build.getNumber(),
        build.getId(),
        travisLog.length());
    return travisLog;
  }

  public String getLog(V3Build build) {
    String travisLog =
        build.getJobs().stream()
            .map(V3Job::getId)
            .map(this::getJobLog)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    log.info(
        "fetched logs for [{}:{}], [buildId:{}], [logLength:{}]",
        build.branchedRepoSlug(),
        build.getNumber(),
        build.getId(),
        travisLog.length());
    return travisLog;
  }

  public boolean isLogReady(V3Build build) {
    if (!legacyLogFetching) {
      boolean logComplete = build.getLogComplete();
      if (logComplete) {
        return true;
        // If not complete, we still want to try to fetch the log to check, because the logs are
        // always completed for a while before the log_complete flag turns true
      }
    }
    return isLogReady(build.getJobs().stream().map(V3Job::getId).collect(Collectors.toList()));
  }

  public boolean isLogReady(List<Integer> jobIds) {
    return jobIds.stream().map(this::getAndCacheJobLog).allMatch(Optional::isPresent);
  }

  @SuppressWarnings("rawtypes")
  private Optional<String> getAndCacheJobLog(int jobId) {
    log.debug("fetching log by jobId {}", jobId);
    String cachedLog = travisCache.getJobLog(groupKey, jobId);
    if (cachedLog != null) {
      log.debug("Found log for jobId {} in the cache", jobId);
      return Optional.of(cachedLog);
    }
    try {
      V3Log v3Log = Retrofit2SyncCall.execute(travisClient.jobLog(getAccessToken(), jobId));
      if (v3Log != null && v3Log.isReady()) {
        log.info("Log for jobId {} was ready, caching it", jobId);
        travisCache.setJobLog(groupKey, jobId, v3Log.getContent());
        return Optional.of(v3Log.getContent());
      }
    } catch (SpinnakerServerException e) {
      if (e instanceof SpinnakerHttpException) { // only SpinnakerHttpException has a response body
        Map<String, Object> body = ((SpinnakerHttpException) e).getResponseBody();
        if (body != null && "log_expired".equals(body.get("error_type"))) {
          log.info(
              "{}: The log for job id {} has expired and the corresponding build was ignored",
              groupKey,
              jobId);
        } else {
          log.warn(
              "{}: Could not get log for job id {}. Error from Travis:\n{}", groupKey, jobId, body);
        }
      }
    }

    return Optional.empty();
  }

  public String getJobLog(int jobId) {
    Optional<String> jobLog = getAndCacheJobLog(jobId);
    if (jobLog.isPresent()) {
      return jobLog.get();
    } else {
      log.warn("Incomplete log for jobId {}! This is not supposed to happen.", jobId);
      return null;
    }
  }

  public GenericBuild getGenericBuild(Build build, String repoSlug) {
    GenericBuild genericBuild = TravisBuildConverter.genericBuild(build, repoSlug, baseUrl);
    boolean logReady = isLogReady(build.getJob_ids());
    if (logReady) {
      parseAndDecorateArtifacts(getLog(build), genericBuild);
    } else {
      genericBuild.setResult(Result.BUILDING);
    }
    return genericBuild;
  }

  public GenericBuild getGenericBuild(V3Build build) {
    return getGenericBuild(build, false);
  }

  public GenericBuild getGenericBuild(V3Build build, boolean fetchLogs) {
    GenericBuild genericBuild = TravisBuildConverter.genericBuild(build, baseUrl);
    if (fetchLogs) {
      parseAndDecorateArtifacts(getLog(build), genericBuild);
    }
    return genericBuild;
  }

  @Override
  public JobConfiguration getJobConfig(String inputRepoSlug) {
    String repoSlug = cleanRepoSlug(inputRepoSlug);
    V3Builds builds =
        Retrofit2SyncCall.execute(
            travisClient.v3builds(getAccessToken(), repoSlug, 1, "job.config"));
    final Optional<Config> config =
        builds.getBuilds().stream()
            .findFirst()
            .flatMap(build -> build.getJobs().stream().findFirst())
            .map(V3Job::getConfig);
    return new GenericJobConfiguration(
        extractRepoFromRepoSlug(repoSlug),
        extractRepoFromRepoSlug(repoSlug),
        repoSlug,
        true,
        getUrl(repoSlug),
        false,
        config.map(Config::getParameterDefinitionList).orElse(null));
  }

  private static String extractRepoFromRepoSlug(String repoSlug) {
    return repoSlug.split("/")[1];
  }

  public String getUrl(String repoSlug) {
    return baseUrl + "/" + repoSlug;
  }

  @Override
  public Map<String, Long> queuedBuild(String master, long queueId) {
    Map<String, Long> queuedJob = travisCache.getQueuedJob(groupKey, queueId);
    Request requestResponse =
        Retrofit2SyncCall.execute(
            travisClient.request(
                getAccessToken(), queuedJob.get("repositoryId"), queuedJob.get("requestId")));
    if (!requestResponse.getBuilds().isEmpty()) {
      log.info(
          "{}: Build found: [{}:{}] . Removing {} from {} travisCache.",
          StructuredArguments.kv("group", groupKey),
          requestResponse.getRepository().getSlug(),
          requestResponse.getBuilds().get(0).getNumber(),
          queueId,
          groupKey);
      travisCache.removeQuededJob(groupKey, queueId);
      LinkedHashMap<String, Long> map = new LinkedHashMap<>(1);
      map.put("number", requestResponse.getBuilds().get(0).getNumber());
      return map;
    }
    return null;
  }

  public void syncRepos() {
    try {
      Retrofit2SyncCall.execute(travisClient.usersSync(getAccessToken(), new EmptyObject()));
    } catch (SpinnakerServerException e) {
      log.error(
          "synchronizing travis repositories for {} failed with error: {}",
          groupKey,
          e.getMessage());
    }
  }

  protected static String cleanRepoSlug(String inputRepoSlug) {
    String[] parts = inputRepoSlug.split("/");
    return parts[0] + "/" + parts[1];
  }

  protected static String branchFromRepoSlug(String inputRepoSlug) {
    String branch = extractBranchFromRepoSlug(inputRepoSlug).replaceFirst("^pull_request_", "");
    return branch.equalsIgnoreCase("tags") ? "" : branch;
  }

  protected static boolean branchIsTagsVirtualBranch(String inputRepoSlug) {
    return extractBranchFromRepoSlug(inputRepoSlug).equalsIgnoreCase("tags");
  }

  protected static boolean branchIsPullRequestVirtualBranch(String inputRepoSlug) {
    return extractBranchFromRepoSlug(inputRepoSlug).startsWith("pull_request_");
  }

  protected static TravisBuildType travisBuildTypeFromRepoSlug(String inputRepoSlug) {
    if (branchIsTagsVirtualBranch(inputRepoSlug)) {
      return TravisBuildType.tag;
    }

    if (branchIsPullRequestVirtualBranch(inputRepoSlug)) {
      return TravisBuildType.pull_request;
    }

    if (!branchFromRepoSlug(inputRepoSlug).isEmpty()) {
      return TravisBuildType.branch;
    }

    return TravisBuildType.unknown;
  }

  protected int calculatePagination(int numberOfJobs) {
    int intermediate = numberOfJobs / TRAVIS_JOB_RESULT_LIMIT;
    if (numberOfJobs % TRAVIS_JOB_RESULT_LIMIT > 0) {
      intermediate += 1;
    }
    return intermediate;
  }

  int getLimit(int page, int numberOfBuilds) {
    return page == calculatePagination(numberOfBuilds)
            && (numberOfBuilds % TRAVIS_JOB_RESULT_LIMIT > 0)
        ? (numberOfBuilds % TRAVIS_JOB_RESULT_LIMIT)
        : TRAVIS_JOB_RESULT_LIMIT;
  }

  private static String extractBranchFromRepoSlug(String inputRepoSlug) {
    return Arrays.stream(inputRepoSlug.split("/")).skip(2).collect(Collectors.joining("/"));
  }

  private void setAccessToken() {
    this.accessToken = Retrofit2SyncCall.execute(travisClient.accessToken(gitHubAuth));
  }

  private String getAccessToken() {
    if (accessToken == null) {
      setAccessToken();
    }

    return "token " + accessToken.getAccessToken();
  }

  private String buildCommandKey(String id) {
    return groupKey + "-" + id;
  }

  private void parseAndDecorateArtifacts(String log, GenericBuild genericBuild) {
    genericBuild.setArtifacts(ArtifactParser.getArtifactsFromLog(log, artifactRegexes));
    artifactDecorator.ifPresent(decorator -> decorator.decorate(genericBuild));
  }

  public final String getBaseUrl() {
    return baseUrl;
  }

  public final String getGroupKey() {
    return groupKey;
  }

  public final GithubAuth getGitHubAuth() {
    return gitHubAuth;
  }

  public final int getNumberOfJobs() {
    return numberOfJobs;
  }

  public final TravisClient getTravisClient() {
    return travisClient;
  }

  public final TravisCache getTravisCache() {
    return travisCache;
  }
}
