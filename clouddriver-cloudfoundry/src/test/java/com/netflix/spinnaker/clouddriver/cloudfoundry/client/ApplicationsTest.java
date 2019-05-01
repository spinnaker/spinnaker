/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ApplicationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Application;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Package;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import io.vavr.collection.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.time.ZonedDateTime;
import java.util.*;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup.State.STARTED;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class ApplicationsTest {
  private ApplicationService applicationService = mock(ApplicationService.class);
  private Spaces spaces = mock(Spaces.class);
  private Applications apps = new Applications("pws", "some-apps-man-uri", "some-metrics-uri", applicationService, spaces);
  private String spaceId = "space-guid";
  private CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
    .id(spaceId)
    .name("space-name")
    .organization(CloudFoundryOrganization.builder()
      .id("org-id")
      .name("org-name")
      .build())
    .build();

  @Test
  void errorHandling() {
    CloudFoundryClient client = new HttpCloudFoundryClient("pws", "some.api.uri.example.com", "some-metrics-uri",
      "api.run.pivotal.io", "baduser", "badpassword");

    assertThatThrownBy(() -> client.getApplications().all())
      .isInstanceOf(CloudFoundryApiException.class);
  }

  @Test
  void dontScaleApplicationIfInputsAreNullOrZero() {
    apps.scaleApplication("id", null, null, null);
    apps.scaleApplication("id", 0, 0, 0);

    verify(applicationService, never()).scaleApplication(any(), any());
  }

  @Test
  void scaleApplicationIfInputsAreMixOfNullAndZero() {
    Response successResponse = new Response("http://capi.io", 200, "", Collections.emptyList(), null);
    when(applicationService.scaleApplication(any(), any())).thenReturn(successResponse);

    apps.scaleApplication("id", 0, null, null);

    verify(applicationService).scaleApplication(any(), any());
  }

  @Test
  void findByIdIfInputsAreValid() {
    String serverGroupId = "some-app-guid";
    String serverGroupName = "some-app-name";
    Application application = new Application()
      .setCreatedAt(ZonedDateTime.now())
      .setGuid(serverGroupId)
      .setName(serverGroupName)
      .setState("STARTED")
      .setLinks(HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid")).toJavaMap());

    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance
      .setPlan("service-plan")
      .setServicePlanGuid("service-plan-guid")
      .setTags(new HashSet<>(Arrays.asList("tag1", "tag2")))
      .setName("service-instance");

    ApplicationEnv.SystemEnv systemEnv = new ApplicationEnv.SystemEnv()
      .setVcapServices(HashMap.of("service-name-1", Collections.singletonList(serviceInstance)).toJavaMap());
    ApplicationEnv applicationEnv = new ApplicationEnv()
      .setSystemEnvJson(systemEnv);

    Process process = new Process()
      .setDiskInMb(1024)
      .setGuid("process-guid")
      .setInstances(1)
      .setMemoryInMb(1024);

    Package applicationPacakage = new Package()
      .setData(new PackageData()
        .setChecksum(new PackageChecksum().setType("package-checksum-type").setValue("package-check-sum-value"))
      )
      .setLinks(HashMap.of("download", new Link().setHref("http://capi.io/download/space-guid")).toJavaMap());
    Pagination<Package> packagePagination = new Pagination<Package>()
      .setPagination(new Pagination.Details().setTotalPages(1))
      .setResources(Collections.singletonList(applicationPacakage));

    Droplet droplet = new Droplet()
      .setGuid("droplet-guid")
      .setStack("droplet-stack")
      .setBuildpacks(Collections.singletonList(new Buildpack().setBuildpackName("build-pack-name")));

    CloudFoundryOrganization cloudFoundryOrganization = CloudFoundryOrganization.builder()
      .id("org-id")
      .name("org-name")
      .build();
    CloudFoundrySpace cloudFoundrySpace = CloudFoundrySpace.builder()
      .id("space-id")
      .name("space-name")
      .organization(cloudFoundryOrganization)
      .build();

    when(applicationService.findById(anyString())).thenReturn(application);
    when(applicationService.findApplicationEnvById(anyString())).thenReturn(applicationEnv);
    when(spaces.findById(any())).thenReturn(cloudFoundrySpace);
    when(applicationService.findProcessById(any())).thenReturn(process);
    when(applicationService.instances(anyString())).thenReturn(
      HashMap.of("0", new InstanceStatus().setState(InstanceStatus.State.RUNNING).setUptime(2405L)).toJavaMap());
    when(applicationService.findPackagesByAppId(anyString())).thenReturn(packagePagination);
    when(applicationService.findDropletByApplicationGuid(anyString())).thenReturn(droplet);

    CloudFoundryServerGroup cloudFoundryServerGroup = apps.findById(serverGroupId);
    assertThat(cloudFoundryServerGroup).isNotNull();
    assertThat(cloudFoundryServerGroup.getId()).isEqualTo(serverGroupId);
    assertThat(cloudFoundryServerGroup.getName()).isEqualTo(serverGroupName);
    assertThat(cloudFoundryServerGroup.getAppsManagerUri()).isEqualTo("some-apps-man-uri/organizations/org-id/spaces/space-id/applications/some-app-guid");
    assertThat(cloudFoundryServerGroup.getMetricsUri()).isEqualTo("some-metrics-uri/apps/some-app-guid");
    assertThat(cloudFoundryServerGroup.getServiceInstances().size()).isEqualTo(1);
    assertThat(cloudFoundryServerGroup.getServiceInstances().get(0).getTags()).containsExactly("tag1", "tag2");

    verify(applicationService).findById(serverGroupId);
    verify(applicationService).findApplicationEnvById(serverGroupId);
    verify(applicationService).instances(serverGroupId);
    verify(applicationService).findPackagesByAppId(serverGroupId);
    verify(applicationService).findDropletByApplicationGuid(serverGroupId);
  }

  @Test
  void updateProcess() {
    when(applicationService.updateProcess(any(), any())).thenReturn(new Process());

    apps.updateProcess("guid1", "command1", "http", "/endpoint");
    verify(applicationService).updateProcess("guid1", new UpdateProcess("command1",
      new Process.HealthCheck().setType("http").setData(
        new Process.HealthCheckData().setEndpoint("/endpoint")
      )
    ));

    apps.updateProcess("guid1", "command1", "http", null);
    verify(applicationService).updateProcess("guid1", new UpdateProcess("command1",
      new Process.HealthCheck().setType("http")
    ));

    apps.updateProcess("guid1", "command1", null, null);
    verify(applicationService).updateProcess("guid1", new UpdateProcess("command1", null));
  }

  @Test
  void getProcessState() {
    ProcessStats processStats = new ProcessStats().setState(ProcessStats.State.RUNNING);
    ProcessResources processResources = new ProcessResources().setResources(Collections.singletonList(processStats));
    when(applicationService.findProcessStatsById(anyString())).thenReturn(processResources);
    ProcessStats.State result = apps.getProcessState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.RUNNING);
    verify(applicationService, never()).findById(anyString());
  }

  @Test
  void getProcessStateWhenStatsNotFound() {
    Response errorResponse = new Response("http://capi.io", 404, "Not Found", Collections.emptyList(), null);
    when(applicationService.findProcessStatsById(anyString())).thenThrow(RetrofitError.httpError("http://capi.io", errorResponse, null, null));
    ProcessStats.State result = apps.getProcessState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.DOWN);
    verify(applicationService, never()).findById(anyString());
  }

  @Test
  void getProcessStateWhenStatsIsEmptyListAndAppIsStarted() {
    Application application = new Application()
      .setCreatedAt(ZonedDateTime.now())
      .setGuid("some-app-guid")
      .setName("some-app")
      .setState("STARTED")
      .setLinks(HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid")).toJavaMap());
    ProcessResources processResources = new ProcessResources().setResources(Collections.emptyList());
    when(applicationService.findProcessStatsById(anyString())).thenReturn(processResources);
    when(applicationService.findById(anyString())).thenReturn(application);
    ProcessStats.State result = apps.getProcessState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.RUNNING);
    verify(applicationService).findById("some-app-guid");
  }

  @Test
  void getProcessStateWhenStatsIsEmptyListAndAppIsStopped() {
    Application application = new Application()
      .setCreatedAt(ZonedDateTime.now())
      .setGuid("some-app-guid")
      .setName("some-app")
      .setState("STOPPED")
      .setLinks(HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid")).toJavaMap());
    ProcessResources processResources = new ProcessResources().setResources(Collections.emptyList());
    when(applicationService.findProcessStatsById(anyString())).thenReturn(processResources);
    when(applicationService.findById(anyString())).thenReturn(application);
    ProcessStats.State result = apps.getProcessState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.DOWN);
    verify(applicationService).findById("some-app-guid");
  }

  @ParameterizedTest
  @ValueSource(strings = {"myapp-v999", "myapp"})
  void getTakenServerGroups(String existingApp) {

    when(applicationService.listAppsFiltered(isNull(Integer.class), anyListOf(String.class), anyInt()))
      .thenReturn(Page.singleton(getApplication(existingApp), "123"));

    List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>> taken = apps
      .getTakenSlots("myapp", "space");
    assertThat(taken).first().extracting(app -> app.getEntity().getName()).isEqualTo(existingApp);
  }

  @ParameterizedTest
  @ValueSource(strings = {"myapp-v999", "myapp", "myapp-stack2", "anothername", "myapp-stack-detail"})
  void getTakenServerGroupsWhenNoPriorVersionExists(String similarAppName) {
    com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application application = getApplication(similarAppName);

    when(applicationService.listAppsFiltered(isNull(Integer.class), anyListOf(String.class), anyInt()))
      .thenReturn(Page.singleton(application, "123"));

    List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>> taken = apps
      .getTakenSlots("myapp-stack", "space");
    assertThat(taken).isEmpty();
  }

  @Test
  void getLatestServerGroupCapiDoesntCorrectlyOrderResults() {
    when(applicationService.listAppsFiltered(isNull(Integer.class), anyListOf(String.class), anyInt()))
      .thenReturn(Page.asPage(getApplication("myapp-prod-v046"), getApplication("myapp-v003"), getApplication("myapp")));

    List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>> taken = apps
      .getTakenSlots("myapp", "space");

    assertThat(taken).extracting(app -> app.getEntity().getName()).contains("myapp", "myapp-v003");
  }

  @Test
  void findServerGroupId() {
    String serverGroupName = "server-group";
    String spaceId = "space-guid";
    String expectedServerGroupId = "app-guid";
    Application application = new Application()
      .setCreatedAt(ZonedDateTime.now())
      .setGuid(expectedServerGroupId)
      .setName("app")
      .setState("STARTED")
      .setLinks(HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid")).toJavaMap());
    Pagination<Application> applicationPagination = new Pagination<Application>()
      .setPagination(new Pagination.Details().setTotalPages(1))
      .setResources(Collections.singletonList(application));
    when(applicationService.all(any(), any(), any())).thenReturn(applicationPagination);
    mockMap(cloudFoundrySpace, "droplet-id");

    String serverGroupId = apps.findServerGroupId(serverGroupName, spaceId);

    assertThat(serverGroupId).isEqualTo(expectedServerGroupId);
  }

  @Test
  void findServerGroupByNameAndSpaceId() {
    String serverGroupId = "server-group-guid";
    String serverGroupName = "server-group";
    Application application = new Application()
      .setCreatedAt(ZonedDateTime.now())
      .setGuid(serverGroupId)
      .setName(serverGroupName)
      .setState("STARTED")
      .setLinks(HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid")).toJavaMap());
    Pagination<Application> applicationPagination = new Pagination<Application>()
      .setPagination(new Pagination.Details().setTotalPages(1))
      .setResources(Collections.singletonList(application));
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance
      .setPlan("service-plan")
      .setServicePlanGuid("service-plan-guid")
      .setTags(Collections.emptySet())
      .setName("service-instance");
    String dropletId = "droplet-guid";

    when(applicationService.all(any(), any(), any())).thenReturn(applicationPagination);
    mockMap(cloudFoundrySpace, dropletId);

    CloudFoundryDroplet expectedDroplet = CloudFoundryDroplet.builder().id(dropletId).build();
    CloudFoundryServerGroup expectedCloudFoundryServerGroup = CloudFoundryServerGroup.builder()
      .account("pws")
      .state(STARTED)
      .space(cloudFoundrySpace)
      .droplet(expectedDroplet)
      .id(serverGroupId)
      .env(emptyMap())
      .instances(Collections.emptySet())
      .serviceInstances(Collections.emptyList())
      .createdTime(application.getCreatedAt().toInstant().toEpochMilli())
      .memory(0)
      .diskQuota(0)
      .name(serverGroupName)
      .appsManagerUri("some-apps-man-uri/organizations/org-id/spaces/space-guid/applications/server-group-guid")
      .metricsUri("some-metrics-uri/apps/server-group-guid")
      .ciBuild(CloudFoundryBuildInfo.builder().build())
      .build();

    CloudFoundryServerGroup serverGroup = apps.findServerGroupByNameAndSpaceId(serverGroupName, spaceId);

    assertThat(serverGroup).isEqualToComparingFieldByFieldRecursively(expectedCloudFoundryServerGroup);
    // server group should be cached because of call to "findServerGroupId"
    verify(applicationService, never()).findById(serverGroupId);
  }

  private com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application getApplication(String applicationName) {
    return new com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application()
      .setName(applicationName)
      .setSpaceGuid("space-guid");
  }

  private void mockMap(CloudFoundrySpace cloudFoundrySpace, String dropletId) {
    ApplicationEnv.SystemEnv systemEnv = new ApplicationEnv.SystemEnv()
      .setVcapServices(emptyMap());
    ApplicationEnv applicationEnv = new ApplicationEnv()
      .setSystemEnvJson(systemEnv);
    Process process = new Process()
      .setGuid("process-guid")
      .setInstances(1);
    Package applicationPacakage = new Package()
      .setData(new PackageData()
        .setChecksum(new PackageChecksum().setType("package-checksum-type").setValue("package-check-sum-value"))
      )
      .setLinks(HashMap.of("download", new Link().setHref("http://capi.io/download/space-guid")).toJavaMap());
    Pagination<Package> packagePagination = new Pagination<Package>()
      .setPagination(new Pagination.Details().setTotalPages(1))
      .setResources(Collections.singletonList(applicationPacakage));
    Droplet droplet = new Droplet().setGuid(dropletId);

    when(applicationService.findApplicationEnvById(any())).thenReturn(applicationEnv);
    when(spaces.findById(any())).thenReturn(cloudFoundrySpace);
    when(applicationService.findProcessById(any())).thenReturn(process);
    when(applicationService.instances(any())).thenReturn(emptyMap());
    when(applicationService.findPackagesByAppId(any())).thenReturn(packagePagination);
    when(applicationService.findDropletByApplicationGuid(any())).thenReturn(droplet);
  }
}
