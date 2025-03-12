/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeployOption;
import com.netflix.spinnaker.halyard.deploy.services.v1.DeployService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.proto.DeploymentsGrpc;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.web.bind.annotation.*;

@GRpcService
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments")
public class DeploymentController extends DeploymentsGrpc.DeploymentsImplBase {
  private final DeploymentService deploymentService;
  private final GenerateService generateService;
  private final DeployService deployService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final HalconfigParser halconfigParser;

  @RequestMapping(value = "/{deploymentName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, DeploymentConfiguration> deploymentConfiguration(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<DeploymentConfiguration>builder()
        .getter(() -> deploymentService.getDeploymentConfiguration(deploymentName))
        .validator(() -> deploymentService.validateDeployment(deploymentName))
        .description("Get deployment configuration")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{deploymentName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> deploymentConfiguration(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody DeploymentConfiguration deploymentConfiguration) {
    return GenericUpdateRequest.<DeploymentConfiguration>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(d -> deploymentService.setDeploymentConfiguration(deploymentName, d))
        .validator(() -> deploymentService.validateDeployment(deploymentName))
        .description("Edit deployment configuration")
        .build()
        .execute(validationSettings, deploymentConfiguration);
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<DeploymentConfiguration>> deploymentConfigurations(
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<DeploymentConfiguration>>builder()
        .getter(deploymentService::getAllDeploymentConfigurations)
        .validator(deploymentService::validateAllDeployments)
        .description("Get all deployment configurations")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{deploymentName:.+}/generate/", method = RequestMethod.POST)
  DaemonTask<Halconfig, String> generateConfig(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    Supplier<String> buildResponse =
        () -> {
          GenerateService.ResolvedConfiguration configuration =
              generateService.generateConfigWithOptionalServices(
                  deploymentName,
                  finalServiceNames.stream()
                      .map(SpinnakerService.Type::fromCanonicalName)
                      .collect(Collectors.toList()));
          return configuration.getStagingDirectory();
        };
    StaticRequestBuilder<String> builder = new StaticRequestBuilder<>(buildResponse);
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Generate config");
  }

  @RequestMapping(value = "/{deploymentName:.+}/clean/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> clean(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    Supplier<Void> buildResponse =
        () -> {
          deployService.clean(deploymentName);
          return null;
        };
    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>(buildResponse);
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(
          () -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Clean Deployment of Spinnaker");
  }

  @RequestMapping(value = "/{deploymentName:.+}/connect/", method = RequestMethod.POST)
  DaemonTask<Halconfig, RemoteAction> connect(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames == null ? new ArrayList<>() : serviceNames;
    StaticRequestBuilder<RemoteAction> builder =
        new StaticRequestBuilder<>(
            () -> deployService.connectCommand(deploymentName, finalServiceNames));
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(
          () -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Connect to Spinnaker deployment.");
  }

  @RequestMapping(value = "/{deploymentName:.+}/rollback/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> rollback(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestParam(required = false) List<String> serviceNames,
      @RequestParam(required = false) List<String> excludeServiceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    List<String> finalExcludeServiceNames =
        excludeServiceNames != null ? excludeServiceNames : Collections.emptyList();
    Supplier<Void> buildResponse =
        () -> {
          deployService.rollback(deploymentName, finalServiceNames, finalExcludeServiceNames);
          return null;
        };

    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>(buildResponse);
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(
          () -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(
        builder::build, "Rollback Spinnaker", TimeUnit.MINUTES.toMillis(30));
  }

  @RequestMapping(value = "/{deploymentName:.+}/prep/", method = RequestMethod.POST)
  DaemonTask<Halconfig, RemoteAction> prep(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestParam(required = false) List<String> serviceNames,
      @RequestParam(required = false) List<String> excludeServiceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    List<String> finalExcludeServiceNames =
        excludeServiceNames != null ? excludeServiceNames : Collections.emptyList();
    StaticRequestBuilder<RemoteAction> builder =
        new StaticRequestBuilder<>(
            () -> deployService.prep(deploymentName, finalServiceNames, finalExcludeServiceNames));
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(
        builder::build, "Prep deployment", TimeUnit.MINUTES.toMillis(5));
  }

  @RequestMapping(value = "/{deploymentName:.+}/deploy/", method = RequestMethod.POST)
  DaemonTask<Halconfig, RemoteAction> deploy(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestParam(required = false) List<DeployOption> deployOptions,
      @RequestParam(required = false) List<String> serviceNames,
      @RequestParam(required = false) List<String> excludeServiceNames,
      @RequestParam Optional<Integer> waitForCompletionTimeoutMinutes) {
    List<DeployOption> finalDeployOptions =
        deployOptions != null ? deployOptions : Collections.emptyList();
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    List<String> finalExcludeServiceNames =
        excludeServiceNames != null ? excludeServiceNames : Collections.emptyList();
    StaticRequestBuilder<RemoteAction> builder =
        new StaticRequestBuilder<>(
            () ->
                deployService.deploy(
                    deploymentName,
                    finalDeployOptions,
                    finalServiceNames,
                    finalExcludeServiceNames,
                    waitForCompletionTimeoutMinutes));
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(
        builder::build, "Apply deployment", TimeUnit.MINUTES.toMillis(30));
  }

  public void deployConfig(
      com.netflix.spinnaker.halyard.proto.DeployConfigRequest request,
      io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
    StaticRequestBuilder<RemoteAction> builder =
        new StaticRequestBuilder<>(
            () ->
                deployService.deploy(
                    request.getName(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Optional.empty()));
    builder.setValidateResponse(() -> deploymentService.validateDeployment(request.getName()));
    builder.setSeverity(Severity.WARNING);
    builder.setSetup(
        () ->
            halconfigParser.setInmemoryHalConfig(
                new ByteArrayInputStream(request.getConfig().toByteArray())));

    responseObserver.onNext(
        DaemonTaskHandler.submitTask(
                builder::build, "Apply deployment", TimeUnit.MINUTES.toMillis(30))
            .getLRO());
    responseObserver.onCompleted();
  }

  @RequestMapping(value = "/{deploymentName:.+}/collectLogs/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> collectLogs(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestParam(required = false) List<String> serviceNames,
      @RequestParam(required = false) List<String> excludeServiceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    List<String> finalExcludeServiceNames =
        excludeServiceNames != null ? excludeServiceNames : Collections.emptyList();

    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              deployService.collectLogs(
                  deploymentName, finalServiceNames, finalExcludeServiceNames);
              return null;
            });
    builder.setSeverity(validationSettings.getSeverity());

    if (validationSettings.isValidate()) {
      builder.setValidateResponse(
          () -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Collecting service logs");
  }

  @RequestMapping(value = "/{deploymentName:.+}/configDiff/", method = RequestMethod.GET)
  DaemonTask<Halconfig, NodeDiff> configDiff(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<NodeDiff>builder()
        .getter(() -> deployService.configDiff(deploymentName))
        .validator(() -> deploymentService.validateDeployment(deploymentName))
        .description("Determine config diff")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{deploymentName:.+}/version/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setVersion(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Versions.Version version) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> deploymentService.setVersion(deploymentName, version.getVersion()));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> deploymentService.validateDeploymentShallow(deploymentName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(halconfigParser::undoChanges);
    builder.setSave(halconfigParser::saveConfig);

    return DaemonTaskHandler.submitTask(builder::build, "Edit Spinnaker version");
  }

  @RequestMapping(value = "/{deploymentName:.+}/version/", method = RequestMethod.GET)
  DaemonTask<Halconfig, String> getVersion(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<String>builder()
        .getter(() -> deploymentService.getVersion(deploymentName))
        .validator(() -> deploymentService.validateDeploymentShallow(deploymentName))
        .description("Get Spinnaker version")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(
      value = "/{deploymentName:.+}/details/{serviceName:.+}/",
      method = RequestMethod.GET)
  DaemonTask<Halconfig, RunningServiceDetails> getServiceDetails(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<RunningServiceDetails>builder()
        .getter(() -> null)
        .validator(() -> deploymentService.validateDeployment(deploymentName))
        .description("Get running service details")
        .build()
        .execute(validationSettings);
  }
}
