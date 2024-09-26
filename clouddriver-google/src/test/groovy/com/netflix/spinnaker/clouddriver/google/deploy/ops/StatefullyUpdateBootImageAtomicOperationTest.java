/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.InstanceTemplates.Delete;
import com.google.api.services.compute.Compute.InstanceTemplates.Insert;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerVersion;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.StatefulPolicy;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.google.compute.FakeBatchComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.FakeGoogleComputeOperationRequest;
import com.netflix.spinnaker.clouddriver.google.compute.FakeGoogleComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleServerGroupManagers;
import com.netflix.spinnaker.clouddriver.google.compute.Images;
import com.netflix.spinnaker.clouddriver.google.compute.InstanceTemplates;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.description.StatefullyUpdateBootImageDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceIllegalStateException;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class StatefullyUpdateBootImageAtomicOperationTest {

  private static final String SERVER_GROUP = "testapp-v000";
  private static final String REGION = "us-central1";
  private static final String IMAGE_NAME = "kool-new-os";
  private static final String IMAGE_URL = "http://cloud.google.com/images/my-project/" + IMAGE_NAME;
  private static final String INSTANCE_TEMPLATE_NAME = SERVER_GROUP + "-1234567890";
  private static final String INSTANCE_TEMPLATE_URL =
      "http://cloud.google.com/instance-templates/my-project/" + INSTANCE_TEMPLATE_NAME;

  @Mock private GoogleServerGroupManagers mockServerGroupManagers;
  @Mock private Images mockImages;
  @Mock private InstanceTemplates mockInstanceTemplates;

  private StatefullyUpdateBootImageAtomicOperation operation;

  @BeforeEach
  void setUp() {
    TaskRepository.threadLocalTask.set(new DefaultTask("taskId"));

    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("spinnaker-account")
            .credentials(new FakeGoogleCredentials())
            .project("foo")
            .build();

    GoogleConfigurationProperties config = new GoogleConfigurationProperties();
    config.setBaseImageProjects(ImmutableList.of("projectOne", "projectTwo"));

    GoogleClusterProvider mockClusterProvider = mock(GoogleClusterProvider.class);
    when(mockClusterProvider.getServerGroup(any(), any(), any()))
        .thenReturn(new GoogleServerGroup(SERVER_GROUP).getView());

    StatefullyUpdateBootImageDescription description =
        new StatefullyUpdateBootImageDescription()
            .setServerGroupName(SERVER_GROUP)
            .setRegion(REGION)
            .setBootImage(IMAGE_NAME)
            .setCredentials(credentials);

    GoogleComputeApiFactory mockComputeApiFactory = mock(GoogleComputeApiFactory.class);
    lenient()
        .when(mockComputeApiFactory.createServerGroupManagers(any(), any()))
        .thenReturn(mockServerGroupManagers);
    lenient().when(mockComputeApiFactory.createImages(any())).thenReturn(mockImages);
    lenient()
        .when(mockComputeApiFactory.createInstanceTemplates(any()))
        .thenReturn(mockInstanceTemplates);
    lenient()
        .when(mockComputeApiFactory.createBatchRequest(any()))
        .thenReturn(new FakeBatchComputeRequest<>());

    operation =
        new StatefullyUpdateBootImageAtomicOperation(
            mockClusterProvider, mockComputeApiFactory, config, description);
  }

  @Test
  void couldNotFindImage() throws IOException {
    when(mockImages.get(any(), any())).thenReturn(status404());

    Exception e =
        assertThrows(
            GoogleResourceIllegalStateException.class, () -> operation.operate(ImmutableList.of()));
    assertThat(e).hasMessageContaining(IMAGE_NAME);
  }

  @Test
  void exceptionFindingImage() throws IOException {
    when(mockImages.get(any(), any())).thenThrow(new IOException("uh oh"));

    Exception e = assertThrows(Exception.class, () -> operation.operate(ImmutableList.of()));
    assertThat(e).hasMessageContaining("uh oh");
  }

  @Test
  void multipleInstanceGroupTemplates() throws IOException {
    when(mockImages.get(any(), any())).thenReturn(image(baseImage()));
    when(mockServerGroupManagers.get())
        .thenReturn(
            FakeGoogleComputeRequest.createWithResponse(
                baseInstanceGroupManager()
                    .setVersions(
                        ImmutableList.of(
                            new InstanceGroupManagerVersion(),
                            new InstanceGroupManagerVersion()))));

    Exception e = assertThrows(Exception.class, () -> operation.operate(ImmutableList.of()));
    assertThat(e).hasMessageContaining("more than one instance template");
  }

  @Test
  void noStatefulPolicy() throws IOException {
    when(mockImages.get(any(), any())).thenReturn(image(baseImage()));
    when(mockServerGroupManagers.get())
        .thenReturn(
            FakeGoogleComputeRequest.createWithResponse(
                baseInstanceGroupManager().setStatefulPolicy(null)));

    Exception e = assertThrows(Exception.class, () -> operation.operate(ImmutableList.of()));
    assertThat(e).hasMessageContaining("StatefulPolicy");
  }

  @Test
  void multipleBootDisks() throws IOException {
    when(mockImages.get(any(), any())).thenReturn(image(baseImage()));
    when(mockServerGroupManagers.get())
        .thenReturn(FakeGoogleComputeRequest.createWithResponse(baseInstanceGroupManager()));
    InstanceTemplate instanceTemplate = baseInstanceTemplate();
    instanceTemplate
        .getProperties()
        .setDisks(
            ImmutableList.of(new AttachedDisk().setBoot(true), new AttachedDisk().setBoot(true)));
    when(mockInstanceTemplates.get(any()))
        .thenReturn(FakeGoogleComputeRequest.createWithResponse(instanceTemplate));

    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> operation.operate(ImmutableList.of()));

    assertThat(e).hasMessageContaining("one boot disk");
  }

  @Test
  void success() throws IOException {
    when(mockImages.get(any(), any())).thenReturn(image(new Image().setSelfLink(IMAGE_URL)));
    when(mockServerGroupManagers.get())
        .thenReturn(FakeGoogleComputeRequest.createWithResponse(baseInstanceGroupManager()));
    when(mockInstanceTemplates.get(any()))
        .thenReturn(FakeGoogleComputeRequest.createWithResponse(baseInstanceTemplate()));
    FakeGoogleComputeOperationRequest<Insert> insertOp = new FakeGoogleComputeOperationRequest<>();
    when(mockInstanceTemplates.insert(any())).thenReturn(insertOp);
    FakeGoogleComputeOperationRequest<Delete> deleteOp = new FakeGoogleComputeOperationRequest<>();
    when(mockInstanceTemplates.delete(any())).thenReturn(deleteOp);
    FakeGoogleComputeOperationRequest patchOp = new FakeGoogleComputeOperationRequest();
    when(mockServerGroupManagers.patch(any())).thenReturn(patchOp);

    operation.operate(ImmutableList.of());

    ArgumentCaptor<InstanceTemplate> newTemplateCaptor =
        ArgumentCaptor.forClass(InstanceTemplate.class);
    verify(mockInstanceTemplates).insert(newTemplateCaptor.capture());
    InstanceTemplate newTemplate = newTemplateCaptor.getValue();

    assertThat(newTemplate.getName()).matches(SERVER_GROUP + "-\\d{8}");
    AttachedDisk bootDisk = newTemplate.getProperties().getDisks().get(0);
    assertThat(bootDisk.getInitializeParams().getSourceImage()).isEqualTo(IMAGE_URL);
    assertThat(insertOp.waitedForCompletion()).isTrue();

    ArgumentCaptor<InstanceGroupManager> patchedManagerCaptor =
        ArgumentCaptor.forClass(InstanceGroupManager.class);
    verify(mockServerGroupManagers).patch(patchedManagerCaptor.capture());
    InstanceGroupManager patchedManager = patchedManagerCaptor.getValue();

    assertThat(patchedManager.getInstanceTemplate()).endsWith("/" + newTemplate.getName());
    assertThat(patchedManager.getVersions()).isEmpty();
    assertThat(patchedManager.getUpdatePolicy().getType()).isEqualTo("OPPORTUNISTIC");
    assertThat(patchOp.waitedForCompletion()).isTrue();

    verify(mockInstanceTemplates).delete(INSTANCE_TEMPLATE_NAME);
    assertThat(deleteOp.waitedForCompletion()).isTrue();
  }

  private static Image baseImage() {
    return new Image().setName(IMAGE_NAME).setSelfLink(IMAGE_URL);
  }

  private static FakeGoogleComputeRequest<Compute.Images.Get, Image> image(Image image) {
    return FakeGoogleComputeRequest.createWithResponse(image, mock(Compute.Images.Get.class));
  }

  private static FakeGoogleComputeRequest<Compute.Images.Get, Image> status404()
      throws IOException {
    return FakeGoogleComputeRequest.createWithException(
        GoogleJsonResponseExceptionFactoryTesting.newMock(
            GsonFactory.getDefaultInstance(), HttpStatusCodes.STATUS_CODE_NOT_FOUND, "not found"));
  }

  private static InstanceTemplate baseInstanceTemplate() {
    return new InstanceTemplate()
        .setName(INSTANCE_TEMPLATE_NAME)
        .setSelfLink(INSTANCE_TEMPLATE_URL)
        .setProperties(
            new InstanceProperties()
                .setDisks(
                    ImmutableList.of(
                        new AttachedDisk()
                            .setBoot(true)
                            .setInitializeParams(
                                new AttachedDiskInitializeParams().setSourceImage("centos")),
                        new AttachedDisk().setBoot(false))));
  }

  private static InstanceGroupManager baseInstanceGroupManager() {
    return new InstanceGroupManager()
        .setInstanceTemplate(INSTANCE_TEMPLATE_URL)
        .setVersions(
            ImmutableList.of(
                new InstanceGroupManagerVersion().setInstanceTemplate(INSTANCE_TEMPLATE_URL)))
        .setStatefulPolicy(new StatefulPolicy());
  }
}
