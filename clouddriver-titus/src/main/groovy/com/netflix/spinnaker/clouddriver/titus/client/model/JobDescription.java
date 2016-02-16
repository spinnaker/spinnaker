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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JobDescription {

    private String name;
    private String type;
    private String applicationName;
    private String version;
    private int instances;
    private int cpu;
    private int memory;
    private int disk;
    private int[] ports;
    private Map<String,String> env;
    private boolean allocateIpAddress;

    private String appName;
    private String jobGroupStack;
    private String jobGroupDetail;
    private String user;
    private List<String> softConstraints;
    private List<String> hardConstraints;
    private List<String> tags;

    //Soft/Hard constraints

    JobDescription() {}

    JobDescription(SubmitJobRequest request) {
        type = request.getJobType();
        name = request.getJobName();
        applicationName = request.getDockerImageName();
        version = request.getDockerImageVersion();
        instances = request.getInstances();
        cpu = request.getCpu();
        memory = request.getMemory();
        disk = request.getDisk();
        ports = request.getPorts();
        allocateIpAddress = request.getAllocateIpAddress();
        appName = request.getApplication();
        jobGroupStack = request.getStack();
        jobGroupDetail = request.getDetail();
        softConstraints = request.getConstraints().stream()
                .filter((c) -> c.getConstraintType() == SubmitJobRequest.Constraint.ConstraintType.SOFT)
                .map(SubmitJobRequest.Constraint::getConstraint)
                .collect(Collectors.toList());
        hardConstraints = request.getConstraints().stream()
                .filter((c) -> c.getConstraintType() == SubmitJobRequest.Constraint.ConstraintType.HARD)
                .map(SubmitJobRequest.Constraint::getConstraint)
                .collect(Collectors.toList());
        user = request.getUser();
        env = request.getEnv() != null ? request.getEnv() : new HashMap<>();
        tags = request.getTags() != null ? request.getTags() : new ArrayList<>();
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    public boolean getAllocateIpAddress() {
        return allocateIpAddress;
    }

    public void setAllocateIpAddress(boolean name) {
        this.allocateIpAddress = allocateIpAddress;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getJobGroupStack() {
        return jobGroupStack;
    }

    public void setJobGroupStack(String jobGroupStack) {
        this.jobGroupStack = jobGroupStack;
    }

    public String getJobGroupDetail() {
        return jobGroupDetail;
    }

    public void setJobGroupDetail(String jobGroupDetail) {
        this.jobGroupDetail = jobGroupDetail;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public List<String> getSoftConstraints() {
        return softConstraints;
    }

    public void setSoftConstraints(List<String> softConstraints) {
        this.softConstraints = softConstraints;
    }

    public List<String> getHardConstraints() {
        return hardConstraints;
    }

    public void setHardConstraints(List<String> hardConstraints) {
        this.hardConstraints = hardConstraints;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String,String> getEnv() {
        return env;
    }

    public void setEnv(Map<String,String> env) {
        this.env = env;
    }

    public void setEnvParam(String key, String value) {
        if (this.env == null) {
            this.env = new HashMap<>();
        }
        this.env.put(key, value);
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
