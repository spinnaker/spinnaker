package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import static org.mockito.BDDMockito.*;

import com.google.api.services.run.v1.CloudRun;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeleteCloudrunLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeleteCloudrunLoadBalancerAtomicOperationTest {
  DeleteCloudrunLoadBalancerAtomicOperation deleteCloudrunLoadBalancerAtomicOperation;
  TaskRepository taskRepository;
  DeleteCloudrunLoadBalancerDescription description;
  CloudrunNamedAccountCredentials mockcredentials;
  Task task;
  CloudrunJobExecutor jobExecutor;
  CloudrunLoadBalancerProvider provider;
  CloudrunLoadBalancer loadBalancer;
  JobExecutor executor;

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
            .setProject(" my-project")
            .build(mock(CloudrunJobExecutor.class));
    taskRepository = mock(TaskRepository.class);
    task = mock(Task.class);
    taskRepository.threadLocalTask.set(task);
    provider = mock(CloudrunLoadBalancerProvider.class);
    jobExecutor = mock(CloudrunJobExecutor.class);
    description = new DeleteCloudrunLoadBalancerDescription();
    description.setAccountName("cloudrunaccount");
    description.setLoadBalancerName("LoadBalancer");
    description.setAccount("acc");
    description.setCredentials(mockcredentials);
    deleteCloudrunLoadBalancerAtomicOperation =
        new DeleteCloudrunLoadBalancerAtomicOperation(description);
    loadBalancer = new CloudrunLoadBalancer();
    loadBalancer.setRegion("us-central");
    executor = mock(JobExecutor.class);
  }

  @Test
  public void DeleteCloudrunLoadBalancerOperateTest() throws NoSuchFieldException {

    deleteCloudrunLoadBalancerAtomicOperation =
        new DeleteCloudrunLoadBalancerAtomicOperation(description);
    try {
      Field f = deleteCloudrunLoadBalancerAtomicOperation.getClass().getDeclaredField("provider");
      f.setAccessible(true);
      f.set(deleteCloudrunLoadBalancerAtomicOperation, provider);
      Field f1 =
          deleteCloudrunLoadBalancerAtomicOperation.getClass().getDeclaredField("jobExecutor");
      f1.setAccessible(true);
      f1.set(deleteCloudrunLoadBalancerAtomicOperation, jobExecutor);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Failed to set provider/jobExecutor of DeleteCloudrunLoadBalancerAtomicOperation object",
          e);
    }
    given(provider.getLoadBalancer(any(), anyString())).willReturn(loadBalancer);
    deleteCloudrunLoadBalancerAtomicOperation.operate(new ArrayList<>());
  }
}
