package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.api.services.run.v1.CloudRun;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServiceClient;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DestroyCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunClusterProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DestroyCloudrunAtomicOperationTest {

  DestroyCloudrunAtomicOperation operation;
  TaskRepository taskRepository;
  DestroyCloudrunDescription description;
  CloudrunNamedAccountCredentials mockcredentials;
  Task task;
  CloudrunServiceClient serviceClient;
  CloudrunClusterProvider provider;

  CloudrunServerGroup serverGroup;

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
            .build();
    taskRepository = mock(TaskRepository.class);
    task = mock(Task.class);
    taskRepository.threadLocalTask.set(task);
    provider = mock(CloudrunClusterProvider.class);
    serviceClient = mock(CloudrunServiceClient.class);
    description = new DestroyCloudrunDescription();
    description.setAccountName("cloudrunaccount");
    description.setAccount("acc");
    description.setRegion("region-1");
    description.setServerGroupName("revision-1");
    description.setCredentials(mockcredentials);
    operation = new DestroyCloudrunAtomicOperation(description);
    operation.cloudrunClusterProvider = provider;
    operation.serviceClient = serviceClient;
    serverGroup = new CloudrunServerGroup();
    serverGroup.setRegion("us-central");
  }

  @Test
  public void DestroyServerGroupOperateTest() throws NoSuchFieldException, IOException {

    when(provider.getServerGroup(anyString(), anyString(), anyString())).thenReturn(serverGroup);
    operation.operate(new ArrayList<>());
    verify(serviceClient).deleteRevision("region-1", "revision-1");
  }
}
