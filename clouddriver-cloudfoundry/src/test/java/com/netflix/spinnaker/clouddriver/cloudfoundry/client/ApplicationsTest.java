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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ApplicationEnv;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.InstanceStatus;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Package;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import io.vavr.collection.HashMap;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class ApplicationsTest {
  private ApplicationService applicationService = mock(ApplicationService.class);
  private Spaces spaces = mock(Spaces.class);
  private Applications apps = new Applications("pws", "some-apps-man-uri", applicationService, spaces);

  @Test
  void errorHandling() {
    CloudFoundryClient client = new HttpCloudFoundryClient("pws", "some.api.uri.example.com", "api.run.pivotal.io",
      "baduser", "badpassword");

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
  void findByIdIfInputsAreValid() {
    Application application = new Application()
      .setCreatedAt(ZonedDateTime.now())
      .setGuid("some-app-guid")
      .setName("some-app-name")
      .setState("STARTED")
      .setLinks(HashMap.of("space", new Link().setHref("http://capi.io/space/space-guid")).toJavaMap());

    ApplicationEnv.SystemEnv systemEnv = new ApplicationEnv.SystemEnv()
      .setVcapServices(HashMap.of("service-name-1", Collections.singletonList(new ServiceInstance()
          .setName("service-instance")
          .setPlan("service-plan")
          .setServicePlanGuid("service-plan-guid")
          .setTags(new HashSet<>(Arrays.asList("tag1", "tag2")))
        )).toJavaMap());
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

    when(applicationService.findById("some-app-guid")).thenReturn(application);
    when(applicationService.findApplicationEnvById("some-app-guid")).thenReturn(applicationEnv);
    when(spaces.findById(any())).thenReturn(cloudFoundrySpace);
    when(applicationService.findProcessById(any())).thenReturn(process);
    when(applicationService.instances("some-app-guid")).thenReturn(
      HashMap.of("0", new InstanceStatus().setState(InstanceStatus.State.RUNNING).setUptime(2405L)).toJavaMap());
    when(applicationService.findPackagesByAppId("some-app-guid")).thenReturn(packagePagination);
    when(applicationService.findDropletByApplicationGuid("some-app-guid")).thenReturn(droplet);

    CloudFoundryServerGroup cloudFoundryServerGroup = apps.findById("some-app-guid");
    assertThat(cloudFoundryServerGroup).isNotNull();
    assertThat(cloudFoundryServerGroup.getId()).isEqualTo("some-app-guid");
    assertThat(cloudFoundryServerGroup.getName()).isEqualTo("some-app-name");
    assertThat(cloudFoundryServerGroup.getAppsManagerUri()).isEqualTo("some-apps-man-uri/organizations/org-id/spaces/space-id/applications/some-app-guid");
    assertThat(cloudFoundryServerGroup.getServiceInstances().size()).isEqualTo(1);
    assertThat(cloudFoundryServerGroup.getServiceInstances().get(0).getTags()).containsExactly("tag1", "tag2");
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
  void getProcessState(){
    ProcessStats processStats = new ProcessStats().setState(ProcessStats.State.RUNNING);
    ProcessResources processResources = new ProcessResources().setResources(Collections.singletonList(processStats));
    when(applicationService.findProcessStatsById(anyString())).thenReturn(processResources);
    ProcessStats.State result = apps.getProcessState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.RUNNING);
  }

  @Test
  void getProcessStateWhenStatsNotFound(){
    Response errorResponse = new Response("http://capi.io", 404,"Not Found", Collections.EMPTY_LIST, null);
    when(applicationService.findProcessStatsById(anyString())).thenThrow(RetrofitError.httpError("http://capi.io", errorResponse, null, null));
    ProcessStats.State result = apps.getProcessState("some-app-guid");
    assertThat(result).isEqualTo(ProcessStats.State.DOWN);
  }
}
