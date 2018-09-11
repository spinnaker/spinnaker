/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.core.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Provider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.PROJECT_CLUSTERS;

public class ProjectClustersCachingAgent implements CachingAgent, CustomScheduledAgent {

  private final static Logger log = LoggerFactory.getLogger(ProjectClustersCachingAgent.class);

  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(15);

  private final Collection<AgentDataType> types = Collections.singletonList(
    AUTHORITATIVE.forType(PROJECT_CLUSTERS.ns)
  );

  private final Front50Service front50Service;
  private final ObjectMapper objectMapper;
  private final Provider<List<ClusterProvider>> clusterProviders;

  public ProjectClustersCachingAgent(Front50Service front50Service,
                                     ObjectMapper objectMapper,
                                     Provider<List<ClusterProvider>> clusterProviders) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.clusterProviders = clusterProviders;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    List<Map> projects = front50Service.searchForProjects(Collections.emptyMap(), 1000);

    Map<String, List<ClusterModel>> projectClusters = new ConcurrentHashMap<>();

    for (Map projectMap : projects) {
      Project project;
      try {
        project = objectMapper.convertValue(projectMap, Project.class);
      } catch (IllegalArgumentException e) {
        String projectName = (String) projectMap.getOrDefault("name", "UNKNOWN");
        log.error("Could not marshal project '{}' to internal model", projectName, e);
        continue;
      }

      if (project.config.clusters.isEmpty()) {
        projectClusters.put(project.name, Collections.emptyList());
        continue;
      }

      List<String> applicationsToRetrieve = Optional.ofNullable(project.config.applications)
        .orElse(Collections.emptyList());
      Map<String, Set<Cluster>> allClusters = retrieveClusters(applicationsToRetrieve);

      List<ClusterModel> clusters = project.config.clusters.stream()
        .map(projectCluster -> {
          List<String> applications = Optional.ofNullable(projectCluster.applications).orElse(project.config.applications);
          List<ApplicationClusterModel> applicationModels = applications.stream()
            .map(application -> {
              Set<Cluster> appClusters = allClusters.get(application);
              Set<Cluster> clusterMatches = findClustersForProject(appClusters, projectCluster);
              return new ApplicationClusterModel(application, clusterMatches);
            })
            .collect(Collectors.toList());

          return new ClusterModel(
            projectCluster.account,
            projectCluster.stack,
            projectCluster.detail,
            applicationModels
          );
        })
        .collect(Collectors.toList());

      projectClusters.put(project.name, clusters);
    }

    return new DefaultCacheResult(Collections.singletonMap(
      PROJECT_CLUSTERS.ns,
      Collections.singletonList(
        new MutableCacheData("v1", new HashMap<>(projectClusters), Collections.emptyMap())
      )
    ));
  }

  static class MutableCacheData implements CacheData {

    private final String id;
    private final int ttlSeconds = -1;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, Collection<String>> relationships = new HashMap<>();

    public MutableCacheData(String id) {
      this.id = id;
    }

    @JsonCreator
    public MutableCacheData(String id,
                            Map<String, Object> attributes,
                            Map<String, Collection<String>> relationships) {
      this.id = id;
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public int getTtlSeconds() {
      return ttlSeconds;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    @Override
    public Map<String, Collection<String>> getRelationships() {
      return relationships;
    }
  }

  private Map<String, Set<Cluster>> retrieveClusters(List<String> applications) {
    Map<String, Set<Cluster>> allClusters = new HashMap<>();

    for (String application : applications) {
      for (RetrievedClusters clusters : retrieveApplication(application)) {
        allClusters.computeIfAbsent(clusters.application, s -> new HashSet<>())
          .addAll(clusters.clusters);
      }
    }

    return allClusters;
  }

  private Set<Cluster> findClustersForProject(Set<Cluster> appClusters, ProjectCluster projectCluster) {
    if (appClusters == null || appClusters.isEmpty()) {
      return Collections.emptySet();
    }

    return appClusters.stream()
      .filter(appCluster -> {
        Names clusterNameParts = Names.parseName(appCluster.getName());
        return appCluster.getAccountName().equals(projectCluster.account) &&
          nameMatches(clusterNameParts.getStack(), projectCluster.stack) &&
          nameMatches(clusterNameParts.getDetail(), projectCluster.detail);
      })
      .collect(Collectors.toSet());
  }

  private List<RetrievedClusters> retrieveApplication(String application) {
    return clusterProviders.get().stream()
      .map(clusterProvider -> {
        Map<String, Set<Cluster>> details = clusterProvider.getClusterDetails(application);
        if (details == null) {
          return null;
        }
        return new RetrievedClusters(
          application,
          details.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toSet())
        );
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  static boolean nameMatches(String clusterNameValue, String projectClusterValue) {
    if (projectClusterValue == null && clusterNameValue == null) {
      return true;
    }
    if (projectClusterValue != null) {
      return projectClusterValue.equals(clusterNameValue) || "*".equals(projectClusterValue);
    }
    return false;
  }

  @Override
  public long getPollIntervalMillis() {
    return DEFAULT_POLL_INTERVAL_MILLIS;
  }

  @Override
  public long getTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS;
  }

  @Override
  public String getAgentType() {
    return ProjectClustersCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return CoreProvider.PROVIDER_NAME;
  }

  static class Project {
    public String name;
    public ProjectConfig config;
  }

  static class ProjectConfig {
    public List<ProjectCluster> clusters;
    public List<String> applications;
  }

  static class ProjectCluster {
    public String account;
    public String stack;
    public String detail;
    public List<String> applications;
  }

  static class RetrievedClusters {
    public String application;
    public Set<Cluster> clusters;

    public RetrievedClusters(String application, Set<Cluster> clusters) {
      this.application = application;
      this.clusters = clusters;
    }
  }

  public static class ClusterModel {
    public String account;
    public String stack;
    public String detail;
    public List<ApplicationClusterModel> applications;

    public ClusterModel(String account, String stack, String detail, List<ApplicationClusterModel> applications) {
      this.account = account;
      this.stack = stack;
      this.detail = detail;
      this.applications = applications;
    }

    ServerGroup.InstanceCounts getInstanceCounts() {
      ServerGroup.InstanceCounts instanceCounts = new ServerGroup.InstanceCounts();

      applications.stream()
        .flatMap(a -> a.clusters.stream())
        .map(c -> c.instanceCounts)
        .forEach(i -> incrementInstanceCounts(i, instanceCounts));

      return instanceCounts;
    }
  }

  static class ApplicationClusterModel {
    public String application;
    public Set<RegionClusterModel> clusters = new HashSet<>();

    ApplicationClusterModel(String application, Set<Cluster> appClusters) {
      this.application = application;
      Map<String, RegionClusterModel> regionClusters = new HashMap<>();
      appClusters.stream()
        .flatMap(ac -> ac.getServerGroups().stream())
        .filter(serverGroup ->
          serverGroup != null &&
            !serverGroup.isDisabled() &&
            serverGroup.getInstanceCounts().getTotal() > 0)
        .forEach((ServerGroup serverGroup) -> {
          RegionClusterModel regionCluster = regionClusters.computeIfAbsent(
            serverGroup.getRegion(),
            s -> new RegionClusterModel(serverGroup.getRegion())
          );
          incrementInstanceCounts(serverGroup, regionCluster.instanceCounts);

          JenkinsBuildInfo buildInfo = extractJenkinsBuildInfo(serverGroup.getImagesSummary().getSummaries());
          Optional<DeployedBuild> existingBuild = regionCluster.builds.stream()
            .filter(b -> b.buildNumber.equals(buildInfo.number) &&
              Optional.ofNullable(b.host).equals(Optional.ofNullable(buildInfo.host)) &&
              Optional.ofNullable(b.job).equals(Optional.ofNullable(buildInfo.name)))
            .findFirst();

          new OptionalConsumer<>(
            (DeployedBuild b) -> b.deployed = Math.max(b.deployed, serverGroup.getCreatedTime()),
            () -> regionCluster.builds.add(new DeployedBuild(
              buildInfo.host,
              buildInfo.name,
              buildInfo.number,
              serverGroup.getCreatedTime(),
              getServerGroupBuildInfoImages(serverGroup.getImagesSummary().getSummaries())
            ))
          ).accept(existingBuild);
        });
      clusters.addAll(regionClusters.values());
    }

    Long getLastPush() {
      long lastPush = 0;
      for (RegionClusterModel cluster : clusters) {
        if (cluster.getLastPush() != null && cluster.getLastPush() > lastPush) {
          lastPush = cluster.getLastPush();
        }
      }
      return lastPush;
    }
  }

  static class RegionClusterModel {
    public String region;
    public List<DeployedBuild> builds = new ArrayList<>();
    public ServerGroup.InstanceCounts instanceCounts = new ServerGroup.InstanceCounts();

    public RegionClusterModel(String region) {
      this.region = region;
    }

    Long getLastPush() {
      long max = 0;
      for (DeployedBuild build : builds) {
        if (build.deployed != null && build.deployed > max) {
          max = build.deployed;
        }
      }
      return max;
    }
  }

  static class JenkinsBuildInfo {
    public String number;
    public String host;
    public String name;

    public JenkinsBuildInfo() {
      this("0", null, null);
    }

    public JenkinsBuildInfo(String number, String host, String name) {
      this.number = number;
      this.host = host;
      this.name = name;
    }
  }

  static class DeployedBuild {
    public String host;
    public String job;
    public String buildNumber;
    public Long deployed;
    public List images;

    public DeployedBuild(String host, String job, String buildNumber, Long deployed, List images) {
      this.host = host;
      this.job = job;
      this.buildNumber = buildNumber;
      this.deployed = deployed;
      this.images = images;
    }
  }

  private static void incrementInstanceCounts(ServerGroup source, ServerGroup.InstanceCounts target) {
    incrementInstanceCounts(source.getInstanceCounts(), target);
  }

  private static void incrementInstanceCounts(ServerGroup.InstanceCounts source, ServerGroup.InstanceCounts target) {
    target.setTotal(target.getTotal() + source.getTotal());
    target.setUp(target.getUp() + source.getUp());
    target.setDown(target.getDown() + source.getDown());
    target.setOutOfService(target.getOutOfService() + source.getOutOfService());
    target.setStarting(target.getStarting() + source.getStarting());
    target.setUnknown(target.getUnknown() + source.getUnknown());
  }

  @Nonnull
  private static JenkinsBuildInfo extractJenkinsBuildInfo(List<? extends ServerGroup.ImageSummary> imageSummaries) {
    if (imageSummaries.isEmpty()) {
      return new JenkinsBuildInfo();
    }
    ServerGroup.ImageSummary imageSummary = imageSummaries.get(0);

    Map<String, Object> buildInfo = imageSummary.getBuildInfo();
    if (buildInfo == null || !buildInfo.containsKey("jenkins")) {
      return new JenkinsBuildInfo();
    }
    if (!(buildInfo.get("jenkins") instanceof Map)) {
      return new JenkinsBuildInfo();
    }
    Map jenkinsBuildInfo = (Map) buildInfo.get("jenkins");

    String buildNumber = (String) jenkinsBuildInfo.getOrDefault("number", "0");
    String host = (String) jenkinsBuildInfo.get("host");
    String job = (String) jenkinsBuildInfo.get("name");

    return new JenkinsBuildInfo(buildNumber, host, job);
  }

  private static List getServerGroupBuildInfoImages(List<? extends ServerGroup.ImageSummary> imageSummaries) {
    if (imageSummaries.isEmpty()) {
      return null;
    }
    ServerGroup.ImageSummary imageSummary = imageSummaries.get(0);
    Map<String, Object> buildInfo = imageSummary.getBuildInfo();
    if (buildInfo == null || !buildInfo.containsKey("images")) {
      return null;
    }

    return (List) buildInfo.get("images");
  }

  private static class OptionalConsumer<T> implements Consumer<Optional<T>> {

    public static <T> OptionalConsumer<T> of(Consumer<T> consumer, Runnable runnable) {
      return new OptionalConsumer<>(consumer, runnable);
    }

    private final Consumer<T> consumer;
    private final Runnable runnable;

    OptionalConsumer(Consumer<T> consumer, Runnable runnable) {
      super();
      this.consumer = consumer;
      this.runnable = runnable;
    }

    @Override
    public void accept(Optional<T> t) {
      if (t.isPresent()) {
        consumer.accept(t.get());
      } else {
        runnable.run();
      }
    }
  }
}
