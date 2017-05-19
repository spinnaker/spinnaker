package com.netflix.spinnaker.halyard.controllers.v1.dcos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.ClusterService;
import com.netflix.spinnaker.halyard.controllers.v1.DefaultControllerValues;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

/**
 * Controller for adding clusters to the DC/OS provider
 */
@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/clusters")
public class ClusterController {
  @Autowired
  ClusterService clusterService;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Cluster>> clusters(@PathVariable String deploymentName, @PathVariable String providerName,
                                                    @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
                                                    @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity) {
    DaemonResponse.StaticRequestBuilder<List<Cluster>> builder = new DaemonResponse.StaticRequestBuilder<>();
    builder.setBuildResponse(() -> clusterService.getAllClusters(deploymentName, providerName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> clusterService.validateAllClusters(deploymentName, providerName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all " + providerName + " clusters");
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Cluster> cluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity) {
    DaemonResponse.StaticRequestBuilder<Cluster> builder = new DaemonResponse.StaticRequestBuilder<>();
    builder.setBuildResponse(() -> clusterService.getProviderCluster(deploymentName, providerName, clusterName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> clusterService.validateCluster(deploymentName, providerName, clusterName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get " + clusterName + " cluster");
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity) {
    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();

    builder.setUpdate(() -> clusterService.deleteCluster(deploymentName, providerName, clusterName));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> clusterService.validateAllClusters(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Delete the " + clusterName + " cluster");
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity,
      @RequestBody Object rawCluster) {
    Cluster cluster = objectMapper.convertValue(
        rawCluster,
        Providers.translateClusterType(providerName)
    );

    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();

    builder.setUpdate(() -> clusterService.setCluster(deploymentName, providerName, clusterName, cluster));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> clusterService.validateCluster(deploymentName, providerName, cluster.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + clusterName + " cluster");
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity,
      @RequestBody Object rawCluster) {
    Cluster cluster = objectMapper.convertValue(
        rawCluster,
        Providers.translateClusterType(providerName)
    );

    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();
    builder.setSeverity(severity);

    builder.setUpdate(() -> clusterService.addCluster(deploymentName, providerName, cluster));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> clusterService.validateCluster(deploymentName, providerName, cluster.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Add the " + cluster.getName() + " cluster");
  }
}
