package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.api.services.run.v1.CloudRun;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.CloudrunAllocationDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.CloudrunTrafficSplitDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.UpsertCloudrunLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpsertCloudrunLoadBalancerAtomicOperationTest {

  UpsertCloudrunLoadBalancerAtomicOperation upsertCloudrunLoadBalancerAtomicOperation;
  TaskRepository taskRepository;
  UpsertCloudrunLoadBalancerDescription description;
  CloudrunNamedAccountCredentials mockcredentials;
  Task task;
  CloudrunJobExecutor jobExecutor;
  CloudrunTrafficSplitDescription splitDescription;

  CloudrunAllocationDescription allocationDescription;

  @BeforeEach
  public void init() {

    mockcredentials =
        new CloudrunNamedAccountCredentials.Builder()
            .setName("cloudrunaccount")
            .setAccountType("cloudrun")
            .setCloudProvider("cloudrun")
            .setApplicationName("my app")
            .setCredentials(mock(CloudrunCredentials.class))
            .setCloudRun(mock(CloudRun.class))
            .setEnvironment("environment")
            .setJsonKey("jsonkey")
            .setLiveLookupsEnabled(false)
            .setLocalRepositoryDirectory("/localdirectory")
            .setJsonPath("/jsonpath")
            .setProject(" my project")
            .build(mock(CloudrunJobExecutor.class));
    taskRepository = mock(TaskRepository.class);
    task = mock(Task.class);
    taskRepository.threadLocalTask.set(task);
    jobExecutor = mock(CloudrunJobExecutor.class);
    description = new UpsertCloudrunLoadBalancerDescription();
    description.setAccountName("cloudrunaccount");
    description.setLoadBalancerName("LoadBalancer");
    description.setAccount("acc");
    description.setRegion("us-central");
    description.setCredentials(mockcredentials);
    splitDescription = new CloudrunTrafficSplitDescription();
    allocationDescription = new CloudrunAllocationDescription();
    allocationDescription.setRevisionName("revision-1");
    allocationDescription.setPercent(90);
    splitDescription.setAllocationDescriptions(List.of(allocationDescription));
    description.setSplitDescription(splitDescription);
    upsertCloudrunLoadBalancerAtomicOperation =
        new UpsertCloudrunLoadBalancerAtomicOperation(description);
    upsertCloudrunLoadBalancerAtomicOperation.jobExecutor = jobExecutor;
  }

  @Test
  public void UpsertCloudrunLoadBalancerOperateTest() {
    Map<String, Map<String, Map<String, String>>> s =
        Map.of("loadBalancers", Map.of("us-central", Map.of("name", "LoadBalancer")));
    assertTrue(upsertCloudrunLoadBalancerAtomicOperation.operate(new ArrayList<>()).equals(s));
    verify(jobExecutor, times(1)).runCommand(any());
  }
}
