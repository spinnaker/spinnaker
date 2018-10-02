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
package com.netflix.spinnaker.clouddriver.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RetrofitError;

import javax.annotation.Nonnull;
import javax.inject.Provider;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProjectClustersService {

  private final static Logger log = LoggerFactory.getLogger(ProjectClustersService.class);

  private final Front50Service front50Service;
  private final ObjectMapper objectMapper;
  private final Provider<List<ClusterProvider>> clusterProviders;

  public ProjectClustersService(Front50Service front50Service,
                                ObjectMapper objectMapper,
                                Provider<List<ClusterProvider>> clusterProviders) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.clusterProviders = clusterProviders;
  }

  public Map<String, List<ClusterModel>> getProjectClusters(List<String> projectNames) {
    Map<String, List<ProjectClustersService.ClusterModel>> projectClusters = new HashMap<>();

    for (String projectName : projectNames) {
      try {
        Map projectMap = front50Service.getProject(projectName);

        Project project;
        try {
          project = objectMapper.convertValue(projectMap, Project.class);
        } catch (IllegalArgumentException e) {
          log.error("Could not marshal project '{}' to internal model", projectName, e);
          continue;
        }

        if (project.config.clusters.isEmpty()) {
          projectClusters.put(project.name, Collections.emptyList());
          log.debug("Project '{}' does not have any clusters", projectName);
          continue;
        }

        projectClusters.put(project.name, getProjectClusters(project));
      } catch (Exception e) {
        log.error("Unable to fetch clusters for project '{}'", projectName, e);
      }
    }

    return projectClusters;
  }

  public List<ClusterModel> getProjectClusters(String projectName) {
    Map projectData = front50Service.getProject(projectName);

    if (projectData == null) {
      return null;
    }

    Project project;
    try {
      project = objectMapper.convertValue(projectData, Project.class);
    } catch (IllegalArgumentException e) {
      throw new MalformedProjectDataException("Could not marshal project to internal model: " + projectName, e);
    }

    return getProjectClusters(project);
  }

  public List<ClusterModel> getProjectClusters(Project project) {
    List<String> applicationsToRetrieve = Optional.ofNullable(project.config.applications)
      .orElse(Collections.emptyList());
    Map<String, Set<Cluster>> allClusters = retrieveClusters(applicationsToRetrieve, project);

    return project.config.clusters.stream()
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
  }

  private Map<String, Set<Cluster>> retrieveClusters(List<String> applications, Project project) {
    Map<String, Set<Cluster>> allClusters = new HashMap<>();

    for (String application : applications) {
      for (RetrievedClusters clusters : retrieveClusters(application, project)) {
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

  private List<RetrievedClusters> retrieveClusters(String application, Project project) {
    return clusterProviders.get().stream()
      .map(clusterProvider -> {
        Map<String, Set<Cluster>> clusterSummariesByAccount = clusterProvider.getClusterSummaries(application);
        if (clusterSummariesByAccount == null) {
          return null;
        }

        Set<Cluster> allClusterSummaries = clusterSummariesByAccount
          .values()
          .stream()
          .flatMap(Collection::stream)
          .collect(Collectors.toSet());

        Set<Cluster> matchingClusterSummaries = new HashSet<>();
        for (ProjectCluster projectCluster : project.config.clusters) {
          matchingClusterSummaries.addAll(findClustersForProject(allClusterSummaries, projectCluster));
        }

        Set<Cluster> expandedClusters = matchingClusterSummaries
          .stream()
          .map(c -> clusterProvider.getCluster(c.getMoniker().getApp(), c.getAccountName(), c.getName()))
          .collect(Collectors.toSet());

        return new RetrievedClusters(application, expandedClusters);
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

  public static class Project {
    public String name;
    public ProjectConfig config;
  }

  public static class ProjectConfig {
    public List<ProjectCluster> clusters;
    public List<String> applications;
  }

  public static class ProjectCluster {
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

    @JsonProperty
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

    @JsonProperty
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

  public static class MalformedProjectDataException extends RuntimeException {
    MalformedProjectDataException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
