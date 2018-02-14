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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.netflix.spinnaker.halyard.proto.DeploymentsGrpc;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@GRpcService
@RestController
@RequestMapping("/v1/config/deployments")
public class DeploymentController extends DeploymentsGrpc.DeploymentsImplBase{

  @Autowired
  DeploymentService deploymentService;

  @Autowired
  GenerateService generateService;

  @Autowired
  DeployService deployService;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired
  HalconfigParser halconfigParser;

  @RequestMapping(value = "/{deploymentName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, DeploymentConfiguration> deploymentConfiguration(
      @PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<DeploymentConfiguration> builder = new StaticRequestBuilder<>(
        () -> deploymentService.getDeploymentConfiguration(deploymentName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get deployment configuration");
  }

  @RequestMapping(value = "/{deploymentName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> deploymentConfiguration(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawDeployment) {
    DeploymentConfiguration deploymentConfiguration = objectMapper.convertValue(
        rawDeployment,
        DeploymentConfiguration.class
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> deploymentConfiguration.stageLocalFiles(configPath));
    builder.setSeverity(severity);
    builder.setUpdate(() -> deploymentService
        .setDeploymentConfiguration(deploymentName, deploymentConfiguration));

    if (validate) {
      builder.setValidate(() -> deploymentService.validateDeployment(deploymentName));
    } else {
      builder.setValidate(ProblemSet::new);
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit deployment configuration");
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<DeploymentConfiguration>> deploymentConfigurations(
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<DeploymentConfiguration>> builder = new StaticRequestBuilder<>(
        () -> deploymentService.getAllDeploymentConfigurations());
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateAllDeployments());
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all deployment configurations");
  }

  @RequestMapping(value = "/{deploymentName:.+}/generate/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> generateConfig(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    Supplier buildResponse = () -> {
      generateService.generateConfig(deploymentName, finalServiceNames.stream()
          .map(SpinnakerService.Type::fromCanonicalName)
          .collect(Collectors.toList()));
      return null;
    };
    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>(buildResponse);
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Generate config");
  }

  @RequestMapping(value = "/{deploymentName:.+}/clean/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> clean(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    Supplier buildResponse = () -> {
      deployService.clean(deploymentName);
      return null;
    };
    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>(buildResponse);
    builder.setSeverity(severity);

    if (validate) {
      builder
          .setValidateResponse(() -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Clean Deployment of Spinnaker");
  }

  @RequestMapping(value = "/{deploymentName:.+}/connect/", method = RequestMethod.POST)
  DaemonTask<Halconfig, RemoteAction> connect(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames == null ? new ArrayList<>() : serviceNames;
    StaticRequestBuilder<RemoteAction> builder = new StaticRequestBuilder<>(
        () -> deployService.connectCommand(deploymentName, finalServiceNames));
    builder.setSeverity(severity);

    if (validate) {
      builder
          .setValidateResponse(() -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Connect to Spinnaker deployment.");
  }

  @RequestMapping(value = "/{deploymentName:.+}/rollback/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> rollback(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    Supplier buildResponse = () -> {
      deployService.rollback(deploymentName, finalServiceNames);
      return null;
    };

    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>(buildResponse);
    builder.setSeverity(severity);

    if (validate) {
      builder
          .setValidateResponse(() -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler
        .submitTask(builder::build, "Rollback Spinnaker", TimeUnit.MINUTES.toMillis(30));
  }

  @RequestMapping(value = "/{deploymentName:.+}/prep/", method = RequestMethod.POST)
  DaemonTask<Halconfig, RemoteAction> prep(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    StaticRequestBuilder<RemoteAction> builder = new StaticRequestBuilder<>(
        () -> deployService.prep(deploymentName, finalServiceNames));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler
        .submitTask(builder::build, "Prep deployment", TimeUnit.MINUTES.toMillis(5));
  }

  @RequestMapping(value = "/{deploymentName:.+}/deploy/", method = RequestMethod.POST)
  DaemonTask<Halconfig, RemoteAction> deploy(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestParam(required = false) List<DeployOption> deployOptions,
      @RequestParam(required = false) List<String> serviceNames) {
    List<DeployOption> finalDeployOptions =
        deployOptions != null ? deployOptions : Collections.emptyList();
    List<String> finalServiceNames = serviceNames != null ? serviceNames : Collections.emptyList();
    StaticRequestBuilder<RemoteAction> builder = new StaticRequestBuilder<>(
        () -> deployService.deploy(deploymentName, finalDeployOptions,
            finalServiceNames));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler
        .submitTask(builder::build, "Apply deployment", TimeUnit.MINUTES.toMillis(30));
  }

  public void deployConfig(com.netflix.spinnaker.halyard.proto.DeployConfigRequest request,
      io.grpc.stub.StreamObserver<com.google.longrunning.Operation> responseObserver) {
    StaticRequestBuilder<RemoteAction> builder = new StaticRequestBuilder<>(
        () -> deployService.deploy(request.getName(), Collections.emptyList(),
            Collections.emptyList()));
    builder.setValidateResponse(() -> deploymentService.validateDeployment(request.getName()));
    builder.setSeverity(Severity.WARNING);
    builder.setSetup(() ->
      halconfigParser.setInmemoryHalConfig(new ByteArrayInputStream(request.getConfig().toByteArray()))
    );

    responseObserver.onNext(DaemonTaskHandler
        .submitTask(builder::build, "Apply deployment", TimeUnit.MINUTES.toMillis(30)).getLRO());
    responseObserver.onCompleted();
  }

  @RequestMapping(value = "/{deploymentName:.+}/collectLogs/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> collectLogs(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestParam(required = false) List<String> serviceNames) {
    List<String> finalServiceNames = serviceNames == null ? new ArrayList<>() : serviceNames;

    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>(() -> {
      deployService.collectLogs(deploymentName, finalServiceNames);
      return null;
    });
    builder.setSeverity(severity);

    if (validate) {
      builder
          .setValidateResponse(() -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Collecting service logs");
  }

  @RequestMapping(value = "/{deploymentName:.+}/configDiff/", method = RequestMethod.GET)
  DaemonTask<Halconfig, NodeDiff> configDiff(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<NodeDiff> builder = new StaticRequestBuilder<>(
        () -> deployService.configDiff(deploymentName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Determine config diff");
  }

  @RequestMapping(value = "/{deploymentName:.+}/version/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setVersion(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Versions.Version version) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> deploymentService.setVersion(deploymentName, version.getVersion()));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> deploymentService.validateDeploymentShallow(deploymentName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit Spinnaker version");
  }

  @RequestMapping(value = "/{deploymentName:.+}/version/", method = RequestMethod.GET)
  DaemonTask<Halconfig, String> getVersion(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<String> builder = new StaticRequestBuilder<>(
        () -> deploymentService.getVersion(deploymentName));
    builder.setSeverity(severity);

    if (validate) {
      builder
          .setValidateResponse(() -> deploymentService.validateDeploymentShallow(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get Spinnaker version");
  }

  @RequestMapping(value = "/{deploymentName:.+}/details/{serviceName:.+}/", method = RequestMethod.GET)
  DaemonTask<Halconfig, RunningServiceDetails> getServiceDetails(
      @PathVariable String deploymentName,
      @PathVariable String serviceName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<RunningServiceDetails> builder = new StaticRequestBuilder<>(() -> null);
    builder.setSeverity(severity);

    // builder.setBuildResponse(() -> deployService.getRunningServiceDetails(deploymentName, serviceName));

    if (validate) {
      builder.setValidateResponse(() -> deploymentService.validateDeployment(deploymentName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get running service details");
  }
}
