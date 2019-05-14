/*
 * Copyright 2018 Netflix, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.netflix.titus.grpc.protogen.TaskStatus;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Task {

  public Task() {}

  public Task(com.netflix.titus.grpc.protogen.Task grpcTask) {
    id = grpcTask.getId();
    state =
        TaskState.from(
            grpcTask.getStatus().getState().name(), grpcTask.getStatus().getReasonCode());
    jobId = grpcTask.getJobId();
    instanceId = grpcTask.getTaskContextOrDefault("v2.taskInstanceId", id);
    host = grpcTask.getTaskContextOrDefault("agent.host", null);
    region = grpcTask.getTaskContextOrDefault("agent.region", null);
    zone = grpcTask.getTaskContextOrDefault("agent.zone", null);
    submittedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.Accepted);
    launchedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.Launched);
    startedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.StartInitiated);
    finishedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.Finished);
    containerIp = grpcTask.getTaskContextOrDefault("task.containerIp", null);
    logLocation = new HashMap<>();
    logLocation.put("ui", grpcTask.getLogLocation().getUi().getUrl());
    logLocation.put("liveStream", grpcTask.getLogLocation().getLiveStream().getUrl());
    HashMap<String, String> s3 = new HashMap<>();
    s3.put("accountId", grpcTask.getLogLocation().getS3().getAccountId());
    s3.put("accountName", grpcTask.getLogLocation().getS3().getAccountName());
    s3.put("region", grpcTask.getLogLocation().getS3().getRegion());
    s3.put("bucket", grpcTask.getLogLocation().getS3().getBucket());
    s3.put("key", grpcTask.getLogLocation().getS3().getKey());
    logLocation.put("s3", s3);
  }

  private Date getTimestampFromStatus(
      com.netflix.titus.grpc.protogen.Task grpcTask, TaskStatus.TaskState state) {
    return grpcTask.getStatusHistoryList().stream()
        .filter(status -> status.getState().equals(state))
        .findFirst()
        .map(status -> new Date(status.getTimestamp()))
        .orElse(null);
  }

  private String id;
  private String jobId;
  private String instanceId;
  private TaskState state;
  private String host;
  private String region;
  private String zone;
  private Date submittedAt;
  private Date launchedAt;
  private Date startedAt;
  private Date finishedAt;
  private String message;
  private Map<String, Object> data;
  private String stdoutLive;
  private String logs;
  private String snapshots;
  private String containerIp;

  private Map<String, Object> logLocation;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public TaskState getState() {
    return state;
  }

  public void setState(TaskState state) {
    this.state = state;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public Date getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Date submittedAt) {
    this.submittedAt = submittedAt;
  }

  public Date getLaunchedAt() {
    return launchedAt;
  }

  public void setLaunchedAt(Date launchedAt) {
    this.launchedAt = launchedAt;
  }

  public Date getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Date startedAt) {
    this.startedAt = startedAt;
  }

  public Date getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Date finishedAt) {
    this.finishedAt = finishedAt;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
  }

  public String getStdoutLive() {
    return stdoutLive;
  }

  public void setStdoutLive(String stdoutLive) {
    this.stdoutLive = stdoutLive;
  }

  public String getLogs() {
    return logs;
  }

  public void setLogs(String logs) {
    this.logs = logs;
  }

  public String getSnapshots() {
    return snapshots;
  }

  public void setSnapshots(String snapshots) {
    this.snapshots = snapshots;
  }

  public String getContainerIp() {
    return containerIp;
  }

  public void setContainerIp(String containerIp) {
    this.containerIp = containerIp;
  }

  public Map<String, Object> getLogLocation() {
    return logLocation;
  }
}
