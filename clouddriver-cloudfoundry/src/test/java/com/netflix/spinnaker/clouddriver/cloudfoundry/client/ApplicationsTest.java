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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup.State.STARTED;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ApplicationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ProcessesService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Application;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Package;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import io.vavr.collection.HashMap;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.Response;
import retrofit2.mock.Calls;

class ApplicationsTest {
  private final ApplicationService applicationService = mock(ApplicationService.class);
  private final ProcessesService processesService = mock(ProcessesService.class);
  private final Processes processes = mock(Processes.class);
  private final Spaces spaces = mock(Spaces.class);
  private final int resultsPerPage = 500;
  private final Applications apps =
      new Applications(
          "pws",
          "some-apps-man-uri",
          "some-metrics-uri",
          applicationService,
          spaces,
          processes,
          resultsPerPage,
          true,
          ForkJoinPool.commonPool());
  private final String spaceId = "space-guid";
  private final CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .id(spaceId)
          .name("space-name")
          .organization(CloudFoundryOrganization.builder().id("org-id").name("org-name").build())
          .build();

  @Test
  void errorHandling() {
    CloudFoundryClient client =
        new HttpCloudFoundryClient(
            "pws",
            "some.api.uri.example.com",
            "some-metrics-uri",
            "api.run.pivotal.io",
            "baduser",
            "badpassword",
            false,
            false,
            false,
            resultsPerPage,
            ForkJoinPool.commonPool(),
            new OkHttpClient().newBuilder(),
            new CloudFoundryConfigurationProperties.ClientConfig());

    assertThatThrownBy(() -> client.getApplications().all(emptyList()))
        .isInstanceOf(CloudFoundryApiException.class);
  }

  @Test
  void findByIdIfInputsAreValid() {
    String serverGroupId = "some-app-guid";
    String serverGroupName = "some-app-name";
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setUpdatedAt(ZonedDateTime.now())
            .setGuid(serverGroupId)
            .setName(serverGroupName)
            .setState("STARTED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());

    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance
        .setPlan("service-plan")
        .setServicePlanGuid("service-plan-guid")
        .setTags(new HashSet<>(Arrays.asList("tag1", "tag2")))
        .setName("service-instance");

    ApplicationEnv.SystemEnv systemEnv =
        new ApplicationEnv.SystemEnv()
            .setVcapServices(
                HashMap.of("service-name-1", Collections.singletonList(serviceInstance))
                    .toJavaMap());
    ApplicationEnv applicationEnv = new ApplicationEnv().setSystemEnvJson(systemEnv);

    Process process =
        new Process().setDiskInMb(1024).setGuid("process-guid").setInstances(1).setMemoryInMb(1024);

    Package applicationPacakage =
        new Package()
            .setData(
                new PackageData()
                    .setChecksum(
                        new PackageChecksum()
                            .setType("package-checksum-type")
                            .setValue("package-check-sum-value")))
            .setLinks(
                HashMap.of("download", new Link().setHref("http://capi.io/download/space-guid"))
                    .toJavaMap());
    Pagination<Package> packagePagination =
        new Pagination<Package>()
            .setPagination(new Pagination.Details().setTotalPages(1))
            .setResources(Collections.singletonList(applicationPacakage));

    Droplet droplet =
        new Droplet()
            .setGuid("droplet-guid")
            .setStack("droplet-stack")
            .setBuildpacks(
                Collections.singletonList(new Buildpack().setBuildpackName("build-pack-name")));

    CloudFoundryOrganization cloudFoundryOrganization =
        CloudFoundryOrganization.builder().id("org-id").name("org-name").build();
    CloudFoundrySpace cloudFoundrySpace =
        CloudFoundrySpace.builder()
            .id("space-id")
            .name("space-name")
            .organization(cloudFoundryOrganization)
            .build();

    when(applicationService.findById(anyString())).thenReturn(Calls.response(application));
    when(applicationService.findApplicationEnvById(anyString()))
        .thenReturn(Calls.response(applicationEnv));
    when(spaces.findById(any())).thenReturn(cloudFoundrySpace);
    when(processes.findProcessById(any())).thenReturn(Optional.of(process));
    when(applicationService.instances(anyString()))
        .thenReturn(
            Calls.response(
                HashMap.of(
                        "0",
                        new InstanceStatus()
                            .setState(InstanceStatus.State.RUNNING)
                            .setUptime(2405L))
                    .toJavaMap()));
    when(applicationService.findPackagesByAppId(anyString()))
        .thenReturn(Calls.response(packagePagination));
    when(applicationService.findDropletByApplicationGuid(anyString()))
        .thenReturn(Calls.response(droplet));

    CloudFoundryServerGroup cloudFoundryServerGroup = apps.findById(serverGroupId);
    assertThat(cloudFoundryServerGroup).isNotNull();
    assertThat(cloudFoundryServerGroup.getId()).isEqualTo(serverGroupId);
    assertThat(cloudFoundryServerGroup.getName()).isEqualTo(serverGroupName);
    assertThat(cloudFoundryServerGroup.getAppsManagerUri())
        .isEqualTo(
            "some-apps-man-uri/organizations/org-id/spaces/space-id/applications/some-app-guid");
    assertThat(cloudFoundryServerGroup.getMetricsUri())
        .isEqualTo("some-metrics-uri/apps/some-app-guid");
    assertThat(cloudFoundryServerGroup.getServiceInstances().size()).isEqualTo(1);
    assertThat(cloudFoundryServerGroup.getServiceInstances().get(0).getTags())
        .containsExactly("tag1", "tag2");

    verify(applicationService).findById(serverGroupId);
    verify(applicationService).findApplicationEnvById(serverGroupId);
    verify(applicationService).instances(serverGroupId);
    verify(applicationService).findPackagesByAppId(serverGroupId);
    verify(applicationService).findDropletByApplicationGuid(serverGroupId);
  }

  @Test
  void allDoesNotSkipVersionedAppWhenOnlySpinnakerManagedTrue() {
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setUpdatedAt(ZonedDateTime.now())
            .setGuid("guid")
            .setName("my-app-v000")
            .setState("STARTED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());

    Pagination<Application> applicationPagination =
        new Pagination<Application>()
            .setPagination(new Pagination.Details().setTotalPages(1))
            .setResources(Collections.singletonList(application));

    when(applicationService.all(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(applicationPagination)));
    when(applicationService.findById(anyString())).thenReturn(Calls.response(application));
    mockMap(cloudFoundrySpace, "droplet-guid");

    List<CloudFoundryApplication> result = apps.all(List.of(spaceId));
    assertThat(result.size()).isEqualTo(1);

    verify(applicationService).all(null, resultsPerPage, null, spaceId);
  }

  @Test
  void allSkipsUnversionedAppWhenOnlySpinnakerManagedTrue() {
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setUpdatedAt(ZonedDateTime.now())
            .setGuid("guid")
            .setName("my-app")
            .setState("STARTED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());

    Pagination<Application> applicationPagination =
        new Pagination<Application>()
            .setPagination(new Pagination.Details().setTotalPages(1))
            .setResources(Collections.singletonList(application));

    when(applicationService.all(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(applicationPagination)));
    when(applicationService.findById(anyString())).thenReturn(Calls.response(application));
    mockMap(cloudFoundrySpace, "droplet-guid");

    List<CloudFoundryApplication> result = apps.all(List.of(spaceId));
    assertThat(result.size()).isEqualTo(0);

    verify(applicationService).all(null, resultsPerPage, null, spaceId);
  }

  @Test
  void getAppStateWhenProcessStateNotFound() {
    when(processes.getProcessState(anyString())).thenReturn(Optional.empty());
    Application app = new Application();
    app.setState("STARTED");
    when(applicationService.findById("some-app-guid"))
        .thenReturn(Calls.response(Response.success(app)));
    ProcessStats.State result = apps.getAppState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.RUNNING);
  }

  @Test
  void getProcessStateWhenStatsIsEmptyListAndAppIsStarted() {
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setGuid("some-app-guid")
            .setName("some-app")
            .setState("STARTED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());
    ProcessResources processResources =
        new ProcessResources().setResources(Collections.emptyList());
    when(processesService.findProcessStatsById(anyString()))
        .thenReturn(Calls.response(Response.success(processResources)));
    when(applicationService.findById(anyString()))
        .thenReturn(Calls.response(Response.success(application)));
    ProcessStats.State result = apps.getAppState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.RUNNING);
    verify(applicationService).findById("some-app-guid");
  }

  @Test
  void getProcessStateWhenStatsIsEmptyListAndAppIsStopped() {
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setGuid("some-app-guid")
            .setName("some-app")
            .setState("STOPPED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());
    ProcessResources processResources =
        new ProcessResources().setResources(Collections.emptyList());
    when(processesService.findProcessStatsById(anyString()))
        .thenReturn(Calls.response(Response.success(processResources)));
    when(applicationService.findById(anyString()))
        .thenReturn(Calls.response(Response.success(application)));
    ProcessStats.State result = apps.getAppState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.DOWN);
    verify(applicationService).findById("some-app-guid");
  }

  @ParameterizedTest
  @ValueSource(strings = {"myapp-v999", "myapp"})
  void getTakenServerGroups(String existingApp) {

    when(applicationService.listAppsFiltered(isNull(), any(), any()))
        .thenReturn(
            Calls.response(Response.success(Page.singleton(getApplication(existingApp), "123"))));

    List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>>
        taken = apps.getTakenSlots("myapp", "space");
    assertThat(taken).first().extracting(app -> app.getEntity().getName()).isEqualTo(existingApp);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"myapp-v999", "myapp", "myapp-stack2", "anothername", "myapp-stack-detail"})
  void getTakenServerGroupsWhenNoPriorVersionExists(String similarAppName) {
    com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application application =
        getApplication(similarAppName);

    when(applicationService.listAppsFiltered(isNull(), any(), any()))
        .thenReturn(Calls.response(Response.success(Page.singleton(application, "123"))));

    List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>>
        taken = apps.getTakenSlots("myapp-stack", "space");
    assertThat(taken).isEmpty();
  }

  @Test
  void getLatestServerGroupCapiDoesntCorrectlyOrderResults() {
    when(applicationService.listAppsFiltered(isNull(), any(), any()))
        .thenReturn(
            Calls.response(
                Response.success(
                    Page.asPage(
                        getApplication("myapp-prod-v046"),
                        getApplication("myapp-v003"),
                        getApplication("myapp")))));

    List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>>
        taken = apps.getTakenSlots("myapp", "space");

    assertThat(taken).extracting(app -> app.getEntity().getName()).contains("myapp", "myapp-v003");
  }

  @Test
  void findServerGroupId() {
    String serverGroupName = "server-group";
    String spaceId = "space-guid";
    String expectedServerGroupId = "app-guid";
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setUpdatedAt(ZonedDateTime.now())
            .setGuid(expectedServerGroupId)
            .setName("app")
            .setState("STARTED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());
    Pagination<Application> applicationPagination =
        new Pagination<Application>()
            .setPagination(new Pagination.Details().setTotalPages(1))
            .setResources(Collections.singletonList(application));
    when(applicationService.all(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(applicationPagination)));
    mockMap(cloudFoundrySpace, "droplet-id");

    String serverGroupId = apps.findServerGroupId(serverGroupName, spaceId);

    assertThat(serverGroupId).isEqualTo(expectedServerGroupId);
  }

  @Test
  void findServerGroupByNameAndSpaceId() {
    String serverGroupId = "server-group-guid";
    String serverGroupName = "server-group";
    Process process = new Process().setDiskInMb(0).setMemoryInMb(0);
    Application application =
        new Application()
            .setCreatedAt(ZonedDateTime.now())
            .setUpdatedAt(ZonedDateTime.now())
            .setGuid(serverGroupId)
            .setName(serverGroupName)
            .setState("STARTED")
            .setLinks(
                HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid"))
                    .toJavaMap());
    Pagination<Application> applicationPagination =
        new Pagination<Application>()
            .setPagination(new Pagination.Details().setTotalPages(1))
            .setResources(Collections.singletonList(application));
    ServiceInstance serviceInstance = new ServiceInstance();
    serviceInstance
        .setPlan("service-plan")
        .setServicePlanGuid("service-plan-guid")
        .setTags(Collections.emptySet())
        .setName("service-instance");
    String dropletId = "droplet-guid";

    when(applicationService.all(any(), any(), any(), any()))
        .thenReturn(Calls.response(Response.success(applicationPagination)));
    when(processes.findProcessById(any())).thenReturn(Optional.of(process));
    mockMap(cloudFoundrySpace, dropletId);

    CloudFoundryDroplet expectedDroplet = CloudFoundryDroplet.builder().id(dropletId).build();
    CloudFoundryServerGroup expectedCloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .account("pws")
            .state(STARTED)
            .space(cloudFoundrySpace)
            .droplet(expectedDroplet)
            .id(serverGroupId)
            .env(emptyMap())
            .instances(Collections.emptySet())
            .serviceInstances(Collections.emptyList())
            .createdTime(application.getCreatedAt().toInstant().toEpochMilli())
            .updatedTime(application.getUpdatedAt().toInstant().toEpochMilli())
            .memory(0)
            .diskQuota(0)
            .name(serverGroupName)
            .appsManagerUri(
                "some-apps-man-uri/organizations/org-id/spaces/space-guid/applications/server-group-guid")
            .metricsUri("some-metrics-uri/apps/server-group-guid")
            .ciBuild(CloudFoundryBuildInfo.builder().build())
            .appArtifact(ArtifactInfo.builder().build())
            .build();

    CloudFoundryServerGroup serverGroup =
        apps.findServerGroupByNameAndSpaceId(serverGroupName, spaceId);

    assertThat(serverGroup).usingRecursiveComparison().isEqualTo(expectedCloudFoundryServerGroup);
    // server group should be cached because of call to "findServerGroupId"
    verify(applicationService, never()).findById(serverGroupId);
  }

  private com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application getApplication(
      String applicationName) {
    return new com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application()
        .setName(applicationName)
        .setSpaceGuid("space-guid");
  }

  private void mockMap(CloudFoundrySpace cloudFoundrySpace, String dropletId) {
    ApplicationEnv.SystemEnv systemEnv = new ApplicationEnv.SystemEnv().setVcapServices(emptyMap());
    ApplicationEnv applicationEnv = new ApplicationEnv().setSystemEnvJson(systemEnv);
    Process process = new Process().setGuid("process-guid").setInstances(1);
    Package applicationPacakage =
        new Package()
            .setData(
                new PackageData()
                    .setChecksum(
                        new PackageChecksum()
                            .setType("package-checksum-type")
                            .setValue("package-check-sum-value")))
            .setLinks(
                HashMap.of("download", new Link().setHref("http://capi.io/download/space-guid"))
                    .toJavaMap());
    Pagination<Package> packagePagination =
        new Pagination<Package>()
            .setPagination(new Pagination.Details().setTotalPages(1))
            .setResources(Collections.singletonList(applicationPacakage));
    Droplet droplet = new Droplet().setGuid(dropletId);

    when(applicationService.findApplicationEnvById(any()))
        .thenReturn(Calls.response(Response.success(applicationEnv)));
    when(spaces.findById(any())).thenReturn(cloudFoundrySpace);
    when(processesService.findProcessById(any()))
        .thenReturn(Calls.response(Response.success(process)));
    when(applicationService.instances(any()))
        .thenReturn(Calls.response(Response.success(emptyMap())));
    when(applicationService.findPackagesByAppId(any()))
        .thenReturn(Calls.response(Response.success(packagePagination)));
    when(applicationService.findDropletByApplicationGuid(any()))
        .thenReturn(Calls.response(Response.success(droplet)));
  }
}
