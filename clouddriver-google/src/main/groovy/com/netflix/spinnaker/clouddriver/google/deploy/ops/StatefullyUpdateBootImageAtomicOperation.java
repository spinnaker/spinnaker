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

import static com.google.common.base.Preconditions.checkState;
import static com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceIllegalStateException.checkResourceState;
import static java.util.stream.Collectors.toList;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.Throwables;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.InstanceTemplates.Get;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerUpdatePolicy;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.google.compute.ComputeBatchRequest;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleServerGroupManagers;
import com.netflix.spinnaker.clouddriver.google.compute.Images;
import com.netflix.spinnaker.clouddriver.google.compute.InstanceTemplates;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.description.StatefullyUpdateBootImageDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceIllegalStateException;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class StatefullyUpdateBootImageAtomicOperation extends GoogleAtomicOperation<Void> {

  private static final String BASE_PHASE = "STATEFULLY_UPDATE_BOOT_IMAGE";

  private static final Random RANDOM = new Random();

  private final GoogleClusterProvider clusterProvider;
  private final GoogleComputeApiFactory computeApiFactory;
  private final GoogleConfigurationProperties googleConfigurationProperties;
  private final StatefullyUpdateBootImageDescription description;

  public StatefullyUpdateBootImageAtomicOperation(
      GoogleClusterProvider clusterProvider,
      GoogleComputeApiFactory computeApiFactory,
      GoogleConfigurationProperties googleConfigurationProperties,
      StatefullyUpdateBootImageDescription description) {
    this.clusterProvider = clusterProvider;
    this.computeApiFactory = computeApiFactory;
    this.googleConfigurationProperties = googleConfigurationProperties;
    this.description = description;
  }

  /*
      curl -X POST -H "Content-Type: application/json" -d '
        [ { "restartWithNewBootImage": {
              "serverGroupName": "myapp-dev-v000",
              "region": "us-east1",
              "bootImage": "centos-7-v20190423",
              "credentials": "my-account-name"
        } } ]' localhost:7002/gce/ops
  */
  @Override
  public Void operate(List priorOutputs) {

    Task task = TaskRepository.threadLocalTask.get();

    GoogleNamedAccountCredentials credentials = description.getCredentials();

    GoogleServerGroup.View serverGroup =
        GCEUtil.queryServerGroup(
            clusterProvider,
            description.getAccount(),
            description.getRegion(),
            description.getServerGroupName());

    try {

      Image image = getImage(task, credentials);

      GoogleServerGroupManagers managers =
          computeApiFactory.createServerGroupManagers(credentials, serverGroup);

      task.updateStatus(
          BASE_PHASE, String.format("Retrieving server group %s.", serverGroup.getName()));
      InstanceGroupManager instanceGroupManager = managers.get().execute();
      checkResourceState(
          instanceGroupManager.getVersions().size() == 1,
          "Found more than one instance template for the server group %s.",
          description.getServerGroupName());
      checkResourceState(
          instanceGroupManager.getStatefulPolicy() != null,
          "Server group %s does not have a StatefulPolicy",
          description.getServerGroupName());

      String oldTemplateName = GCEUtil.getLocalName(instanceGroupManager.getInstanceTemplate());
      InstanceTemplates instanceTemplates = computeApiFactory.createInstanceTemplates(credentials);

      task.updateStatus(
          BASE_PHASE, String.format("Retrieving instance template %s.", oldTemplateName));
      GoogleComputeRequest<Get, InstanceTemplate> request = instanceTemplates.get(oldTemplateName);
      InstanceTemplate template = request.execute();

      String newTemplateName = getNewTemplateName(description.getServerGroupName());
      template.setName(newTemplateName);
      List<AttachedDisk> disks =
          template.getProperties().getDisks().stream()
              .filter(AttachedDisk::getBoot)
              .collect(toList());
      checkState(disks.size() == 1, "Expected exactly one boot disk, found %s", disks.size());
      AttachedDisk bootDisk = disks.get(0);
      bootDisk.getInitializeParams().setSourceImage(image.getSelfLink());

      task.updateStatus(
          BASE_PHASE, String.format("Saving new instance template %s.", newTemplateName));
      instanceTemplates.insert(template).executeAndWait(task, BASE_PHASE);

      instanceGroupManager
          .setInstanceTemplate(
              GCEUtil.buildInstanceTemplateUrl(credentials.getProject(), newTemplateName))
          .setVersions(ImmutableList.of())
          .setUpdatePolicy(new InstanceGroupManagerUpdatePolicy().setType("OPPORTUNISTIC"));

      task.updateStatus(
          BASE_PHASE, String.format("Starting update of server group %s.", serverGroup.getName()));
      managers.patch(instanceGroupManager).executeAndWait(task, BASE_PHASE);

      task.updateStatus(
          BASE_PHASE, String.format("Deleting instance template %s.", oldTemplateName));
      instanceTemplates.delete(oldTemplateName).executeAndWait(task, BASE_PHASE);

      return null;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @NotNull
  private Image getImage(Task task, GoogleNamedAccountCredentials credentials) throws IOException {

    task.updateStatus(BASE_PHASE, "Looking up image " + description.getBootImage());

    Images imagesApi = computeApiFactory.createImages(credentials);

    SettableFuture<Image> foundImage = SettableFuture.create();

    ComputeBatchRequest<Compute.Images.Get, Image> batchRequest =
        computeApiFactory.createBatchRequest(credentials);
    for (String project : getImageProjects(credentials)) {
      GoogleComputeRequest<Compute.Images.Get, Image> request =
          imagesApi.get(project, description.getBootImage());
      batchRequest.queue(request, new ImageListCallback(project, foundImage));
    }
    batchRequest.execute("findImage");

    // If #execute() returned and foundImage still hasn't been set, then we must not have found one.
    // Set an exception to bubble up to the caller. (This does nothing if foundImage was already set
    // with a result.)
    foundImage.setException(
        new GoogleResourceIllegalStateException(
            "Couldn't find an image named " + description.getBootImage()));

    Image image;
    try {
      image = foundImage.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause());
      throw new RuntimeException(e.getCause());
    }

    return image;
  }

  private ImmutableSet<String> getImageProjects(GoogleNamedAccountCredentials credentials) {
    return ImmutableSet.<String>builder()
        .add(credentials.getProject())
        .addAll(credentials.getImageProjects())
        .addAll(googleConfigurationProperties.getBaseImageProjects())
        .build();
  }

  private static class ImageListCallback extends JsonBatchCallback<Image> {

    final String project;
    final SettableFuture<Image> foundImage;

    ImageListCallback(String project, SettableFuture<Image> foundImage) {
      this.project = project;
      this.foundImage = foundImage;
    }

    @Override
    public void onSuccess(Image image, HttpHeaders responseHeaders) {
      foundImage.set(image);
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      // Only a single project (of the many we query) will actually contain this image, so 404s are
      // expected.
      if (e.getCode() != HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        log.warn(
            String.format("Error retrieving images from project %s: %s", project, e.getMessage()));
      }
    }
  }

  private static String getNewTemplateName(String serverGroupName) {
    return String.format("%s-%08d", serverGroupName, RANDOM.nextInt(100000000));
  }
}
