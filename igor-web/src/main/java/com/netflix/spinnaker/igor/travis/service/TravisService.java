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

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.hystrix.SimpleJava8HystrixCommand;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
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
import com.netflix.spinnaker.igor.travis.client.model.Account;
import com.netflix.spinnaker.igor.travis.client.model.Accounts;
import com.netflix.spinnaker.igor.travis.client.model.Build;
import com.netflix.spinnaker.igor.travis.client.model.Builds;
import com.netflix.spinnaker.igor.travis.client.model.Commit;
import com.netflix.spinnaker.igor.travis.client.model.Config;
import com.netflix.spinnaker.igor.travis.client.model.EmptyObject;
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth;
import com.netflix.spinnaker.igor.travis.client.model.Repo;
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest;
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse;
import com.netflix.spinnaker.igor.travis.client.model.v3.Request;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildType;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Job;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.logstash.logback.argument.StructuredArguments;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RetrofitError;

public class TravisService implements BuildOperations, BuildProperties {

  static final int TRAVIS_BUILD_RESULT_LIMIT = 25;

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String baseUrl;
  private final String groupKey;
  private final GithubAuth gitHubAuth;
  private final int numberOfRepositories;
  private final TravisClient travisClient;
  private final TravisCache travisCache;
  private final Collection<String> artifactRegexes;
  private final Optional<ArtifactDecorator> artifactDecorator;
  private final String buildMessageKey;
  private final Permissions permissions;
  private final boolean legacyLogFetching;
  protected AccessToken accessToken;
  private Accounts accounts;

  public TravisService(
      String travisHostId,
      String baseUrl,
      String githubToken,
      int numberOfRepositories,
      TravisClient travisClient,
      TravisCache travisCache,
      Optional<ArtifactDecorator> artifactDecorator,
      Collection<String> artifactRegexes,
      String buildMessageKey,
      Permissions permissions,
      boolean legacyLogFetching) {
    this.numberOfRepositories = numberOfRepositories;
    this.groupKey = travisHostId;
    this.gitHubAuth = new GithubAuth(githubToken);
    this.travisClient = travisClient;
    this.baseUrl = baseUrl;
    this.travisCache = travisCache;
    this.artifactDecorator = artifactDecorator;
    this.artifactRegexes =
        artifactRegexes != null ? new HashSet<>(artifactRegexes) : Collections.emptySet();
    this.buildMessageKey = buildMessageKey;
    this.permissions = permissions;
    this.legacyLogFetching = legacyLogFetching;
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
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getGenericGitRevisions"),
            () -> {
              String repoSlug = cleanRepoSlug(inputRepoSlug);
              if (StringUtils.isNumeric(build.getId())) {
                V3Build v3build = getV3Build(Integer.parseInt(build.getId()));
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
            })
        .execute();
  }

  @Override
  public GenericBuild getGenericBuild(final String inputRepoSlug, final int buildNumber) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getGenericBuild"),
            () -> {
              String repoSlug = cleanRepoSlug(inputRepoSlug);
              Build build = getBuild(repoSlug, buildNumber);
              return getGenericBuild(build, repoSlug);
            })
        .execute();
  }

  @Override
  public int triggerBuildWithParameters(String inputRepoSlug, Map<String, String> queryParameters) {
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
        travisClient.triggerBuild(getAccessToken(), repoSlug, repoRequest);
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

  public V3Build getV3Build(int buildId) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getV3Build"),
            () ->
                travisClient.v3build(
                    getAccessToken(),
                    buildId,
                    "build.commit" + (legacyLogFetching ? "" : "," + includeLogFetching())))
        .execute();
  }

  public Builds getBuilds(String repoSlug, int buildNumber) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getBuildList"),
            () -> travisClient.builds(getAccessToken(), repoSlug, buildNumber))
        .execute();
  }

  public Build getBuild(String repoSlug, int buildNumber) {
    Builds builds = getBuilds(repoSlug, buildNumber);
    return !builds.getBuilds().isEmpty() ? builds.getBuilds().get(0) : null;
  }

  @Override
  public Map<String, Object> getBuildProperties(
      String inputRepoSlug, GenericBuild build, String fileName) {
    try {
      V3Build v3build = getV3Build(Integer.valueOf(build.getId()));
      return PropertyParser.extractPropertiesFromLog(getLog(v3build));
    } catch (Exception e) {
      log.error("Unable to get igorProperties '{}'", kv("job", inputRepoSlug), e);
      return Collections.emptyMap();
    }
  }

  public List<V3Build> getBuilds(Repo repo, int limit) {
    final V3Builds builds =
        travisClient.builds(getAccessToken(), repo.getId(), limit, includeLogFetching());
    log.debug("fetched {} builds", builds.getBuilds().size());
    return builds.getBuilds();
  }

  public List<GenericBuild> getTagBuilds(String repoSlug) {
    // Tags are hard to identify, no filters exist.
    // Increasing the limit to increase the odds for finding some tag builds.
    V3Builds builds =
        travisClient.v3buildsByEventType(
            getAccessToken(),
            repoSlug,
            "push",
            TRAVIS_BUILD_RESULT_LIMIT * 2,
            includeLogFetching());
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
            travisClient.v3builds(
                getAccessToken(),
                repoSlug,
                branch,
                "push",
                TRAVIS_BUILD_RESULT_LIMIT,
                includeLogFetching());
        break;
      case pull_request:
        builds =
            travisClient.v3builds(
                getAccessToken(),
                repoSlug,
                branch,
                "pull_request",
                TRAVIS_BUILD_RESULT_LIMIT,
                includeLogFetching());
        break;
      case unknown:
      default:
        builds =
            travisClient.v3builds(
                getAccessToken(), repoSlug, TRAVIS_BUILD_RESULT_LIMIT, includeLogFetching());
    }

    return builds.getBuilds().stream()
        .filter(this::isLogReady)
        .map(this::getGenericBuild)
        .collect(Collectors.toList());
  }

  public List<Repo> getReposForAccounts() {
    return new SimpleJava8HystrixCommand<List<Repo>>(
            groupKey,
            buildCommandKey("getReposForAccounts"),
            () -> {
              log.debug("fetching repos for relevant accounts only");

              return getAccounts().getAccounts().stream()
                  .filter(Account::isUser)
                  .flatMap(
                      account ->
                          IntStream.range(0, calculatePagination(numberOfRepositories))
                              .mapToObj(
                                  page ->
                                      travisClient.repos(
                                          getAccessToken(),
                                          account.getLogin(),
                                          true,
                                          TRAVIS_BUILD_RESULT_LIMIT,
                                          page * TRAVIS_BUILD_RESULT_LIMIT))
                              .flatMap(repos -> repos.getRepos().stream())
                              .collect(Collectors.toList()).stream())
                  .collect(Collectors.toList());
            },
            (ignored) -> Collections.emptyList())
        .execute();
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
        "fetched logs for [buildNumber:{}], [buildId:{}], [logLength:{}]",
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
        // always completed for a
        // while before the log_complete flag turns true
      }
    }
    return isLogReady(build.getJobs().stream().map(V3Job::getId).collect(Collectors.toList()));
  }

  public boolean isLogReady(List<Integer> jobIds) {
    return jobIds.stream().map(this::getAndCacheJobLog).allMatch(Optional::isPresent);
  }

  private Optional<String> getAndCacheJobLog(int jobId) {
    log.debug("fetching log by jobId {}", jobId);
    String cachedLog = travisCache.getJobLog(groupKey, jobId);
    if (cachedLog != null) {
      log.info("Found log for jobId {} in the cache", jobId);
      return Optional.of(cachedLog);
    }
    V3Log v3Log =
        new SimpleJava8HystrixCommand<>(
                groupKey,
                buildCommandKey("getJobLog"),
                () -> travisClient.jobLog(getAccessToken(), jobId))
            .execute();
    if (v3Log != null && v3Log.isReady()) {
      log.info("Log for jobId {} was ready, caching it", jobId);
      travisCache.setJobLog(groupKey, jobId, v3Log.getContent());
      return Optional.of(v3Log.getContent());
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

  public Map<String, Integer> queuedBuild(int queueId) {
    Map<String, Integer> queuedJob = travisCache.getQueuedJob(groupKey, queueId);
    Request requestResponse =
        travisClient.request(
            getAccessToken(), queuedJob.get("repositoryId"), queuedJob.get("requestId"));
    if (!requestResponse.getBuilds().isEmpty()) {
      log.info(
          "{}: Build found: [{}:{}] . Removing {} from {} travisCache.",
          StructuredArguments.kv("group", groupKey),
          requestResponse.getRepository().getSlug(),
          requestResponse.getBuilds().get(0).getNumber(),
          queueId,
          groupKey);
      travisCache.removeQuededJob(groupKey, queueId);
      LinkedHashMap<String, Integer> map = new LinkedHashMap<>(1);
      map.put("number", requestResponse.getBuilds().get(0).getNumber());
      return map;
    }
    return null;
  }

  public void syncRepos() {
    try {
      travisClient.usersSync(getAccessToken(), new EmptyObject());
    } catch (RetrofitError e) {
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

  protected int calculatePagination(int numberOfBuilds) {
    int intermediate = numberOfBuilds / TRAVIS_BUILD_RESULT_LIMIT;
    if (numberOfBuilds % TRAVIS_BUILD_RESULT_LIMIT > 0) {
      intermediate += 1;
    }
    return intermediate;
  }

  private static String extractBranchFromRepoSlug(String inputRepoSlug) {
    List<String> parts = Arrays.asList(inputRepoSlug.split("/"));
    return parts.subList(2, parts.size()).stream().collect(Collectors.joining("/"));
  }

  private void setAccessToken() {
    this.accessToken = travisClient.accessToken(gitHubAuth);
  }

  private String getAccessToken() {
    if (accessToken == null) {
      setAccessToken();
    }

    return "token " + accessToken.getAccessToken();
  }

  private Accounts getAccounts() {
    if (accounts == null) {
      setAccounts();
    }

    return accounts;
  }

  private void setAccounts() {
    this.accounts = travisClient.accounts(getAccessToken());
    if (log.isDebugEnabled()) {
      log.debug("fetched {} accounts", accounts.getAccounts().size());
      accounts
          .getAccounts()
          .forEach(
              account -> {
                log.debug("account: {}", account.getLogin());
                log.debug("repos: {}", account.getReposCount());
              });
    }
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

  public final int getNumberOfRepositories() {
    return numberOfRepositories;
  }

  public final TravisClient getTravisClient() {
    return travisClient;
  }

  public final TravisCache getTravisCache() {
    return travisCache;
  }

  private String includeLogFetching() {
    return legacyLogFetching ? null : "build.log_complete";
  }
}
