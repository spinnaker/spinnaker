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

package com.netflix.spinnaker.clouddriver.titus.client.model;

import java.util.*;

public class Job {

    public static class TaskSummary {
        private String id;
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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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
    }

    private String id;
    private String name;
    private String type;
    private List<String> tags;
    private String applicationName;
    private String user;
    private String version;
    private String entryPoint;
    private int instances;
    private int cpu;
    private int memory;
    private int disk;
    private int[] ports;
    private Map<String, String> environment;
    private int retries;
    private int runtimeLimitSecs;
    private boolean allocateIpAddress;
    private Date submittedAt;
    private List<TaskSummary> tasks;

    public Job() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    public int getInstances() {
        return instances;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public int getMemory() {
        return memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }

    public int getDisk() {
        return disk;
    }

    public void setDisk(int disk) {
        this.disk = disk;
    }

    public int[] getPorts() {
        return ports;
    }

    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getRuntimeLimitSecs() {
        return runtimeLimitSecs;
    }

    public void setRuntimeLimitSecs(int runtimeLimitSecs) {
        this.runtimeLimitSecs = runtimeLimitSecs;
    }

    public boolean isAllocateIpAddress() {
        return allocateIpAddress;
    }

    public void setAllocateIpAddress(boolean allocateIpAddress) {
        this.allocateIpAddress = allocateIpAddress;
    }

    public Date getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt) {
        this.submittedAt = submittedAt;
    }

    public List<TaskSummary> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskSummary> tasks) {
        this.tasks = tasks;
    }
}
