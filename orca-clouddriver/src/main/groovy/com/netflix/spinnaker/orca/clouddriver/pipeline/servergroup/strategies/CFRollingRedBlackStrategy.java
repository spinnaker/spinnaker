/*
 * Copyright 2019 Pivotal, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.DetermineTargetServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.Manifest;
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage;
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy;
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategySupport;
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import groovy.util.logging.Slf4j;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

import static com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategySupport.getSource;

/**
 * CFRollingRedBlackStrategy is a rolling red/black strategy specifically made for Cloud Foundry
 * to handle the differences in this type of rollout between an IaaS and Cloud Foundry.
 * <p>
 * If you run on any other platform you should probably be using "{@link RollingRedBlackStrategy}"
 */
@Slf4j
@Data
@Component
@AllArgsConstructor
public class CFRollingRedBlackStrategy implements Strategy, ApplicationContextAware {
  private static final Logger log = LoggerFactory.getLogger(CFRollingRedBlackStrategy.class);
  public final String name = "cfrollingredblack";

  private ApplicationContext applicationContext;
  private ArtifactResolver artifactResolver;
  private Optional<PipelineStage> pipelineStage;
  private ResizeStrategySupport resizeStrategySupport;
  private TargetServerGroupResolver targetServerGroupResolver;
  private ObjectMapper objectMapper;
  private OortService oort;
  private static final ThreadLocal<Yaml> yamlParser = ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  @Override
  public List<Stage> composeFlow(Stage stage) {
    if (!pipelineStage.isPresent()) {
      throw new IllegalStateException("Rolling red/black cannot be run without front50 enabled. Please set 'front50.enabled: true' in your orca config.");
    }

    List<Stage> stages = new ArrayList<>();
    RollingRedBlackStageData stageData = stage.mapTo(RollingRedBlackStageData.class);
    AbstractDeployStrategyStage.CleanupConfig cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage);

    Map<String, Object> baseContext = new HashMap<>();
    baseContext.put(cleanupConfig.getLocation().singularType(), cleanupConfig.getLocation().getValue());
    baseContext.put("cluster", cleanupConfig.getCluster());
    baseContext.put("moniker", cleanupConfig.getMoniker());
    baseContext.put("credentials", cleanupConfig.getAccount());
    baseContext.put("cloudProvider", cleanupConfig.getCloudProvider());

    if (stage.getContext().get("targetSize") != null) {
      stage.getContext().put("targetSize", 0);
    }

    if (stage.getContext().get("useSourceCapacity") != null) {
      stage.getContext().put("useSourceCapacity", false);
    }

    ResizeStrategy.Capacity savedCapacity = new ResizeStrategy.Capacity();
    Map<String, Object> manifest = (Map<String, Object>) stage.getContext().get("manifest");
    if (manifest.get("direct") == null) {
      Artifact artifact = objectMapper.convertValue(manifest.get("artifact"), Artifact.class);
      String artifactId = manifest.get("artifactId") != null ? manifest.get("artifactId").toString() : null;
      Artifact boundArtifact = artifactResolver.getBoundArtifactForStage(stage, artifactId, artifact);

      if (boundArtifact == null) {
        throw new IllegalArgumentException("Unable to bind the manifest artifact");
      }

      Response manifestText = oort.fetchArtifact(boundArtifact);
      try {
        Object manifestYml = yamlParser.get().load(manifestText.getBody().in());
        Map<String, List<Map<String, Object>>> applicationManifests = objectMapper
          .convertValue(manifestYml, new TypeReference<Map<String, List<Map<String, Object>>>>() {});
        List<Map<String, Object>> applications = applicationManifests.get("applications");
        Map<String, Object> applicationConfiguration = applications.get(0);
        manifest.put("direct", applicationConfiguration);
        manifest.remove("artifact");
        manifest.remove("artifactId");
      } catch (IOException e) {
        log.warn("Failure fetching/parsing manifests from {}", boundArtifact, e);
        throw new IllegalStateException(e);
      }
    }
    Manifest.Direct directManifestAttributes = objectMapper.convertValue(manifest.get("direct"), Manifest.Direct.class);

    if (!stage.getContext().containsKey("savedCapacity")) {
      int instances = directManifestAttributes.getInstances();
      savedCapacity.setMin(instances);
      savedCapacity.setMax(instances);
      savedCapacity.setDesired(instances);
      stage.getContext().put("savedCapacity", savedCapacity);
      stage.getContext().put("sourceServerGroupCapacitySnapshot", savedCapacity);
    } else {
      Map<String, Integer> savedCapacityMap = (Map<String, Integer>) stage.getContext().get("savedCapacity");
      savedCapacity.setMin(savedCapacityMap.get("min"));
      savedCapacity.setMax(savedCapacityMap.get("max"));
      savedCapacity.setDesired(savedCapacityMap.get("desired"));
    }

    // FIXME: this clobbers the input capacity value (if any). Should find a better way to request a new asg of size 0
    ResizeStrategy.Capacity zeroCapacity = new ResizeStrategy.Capacity();
    zeroCapacity.setMin(0);
    zeroCapacity.setMax(0);
    zeroCapacity.setDesired(0);
    stage.getContext().put("capacity", zeroCapacity);

    // Start off with deploying one instance of the new version
    ((Map<String, Object>) manifest.get("direct")).put("instances", 1);

    Execution execution = stage.getExecution();
    String executionId = execution.getId();
    List<Integer> targetPercentages = stageData.getTargetPercentages();
    if (targetPercentages.isEmpty() || targetPercentages.get(targetPercentages.size() - 1) != 100) {
      targetPercentages.add(100);
    }

    Map<String, Object> findContext = new HashMap<>(baseContext);
    findContext.put("target", TargetServerGroup.Params.Target.current_asg_dynamic);
    findContext.put("targetLocation", cleanupConfig.getLocation());

    Stage dtsgStage = new Stage(execution, DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE, "Determine Deployed Server Group", findContext);
    dtsgStage.setParentStageId(stage.getId());
    dtsgStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
    stages.add(dtsgStage);

    ResizeStrategy.Source source;
    try {
      source = getSource(targetServerGroupResolver, stageData, baseContext);
    } catch (Exception e) {
      source = null;
    }

    if (source == null) {
      log.warn("no source server group -- will perform RRB to exact fallback capacity {} with no disableCluster or scaleDownCluster stages", savedCapacity);
    }

    ResizeStrategy.Capacity sourceCapacity = source == null ?
      savedCapacity :
      resizeStrategySupport.getCapacity(source.getCredentials(), source.getServerGroupName(), source.getCloudProvider(), source.getLocation());

    for (Integer percentage : targetPercentages) {
      Map<String, Object> scaleUpContext = getScalingContext(stage, cleanupConfig, baseContext, savedCapacity, percentage, null);

      log.info("Adding `Grow target to {}% of desired size` stage with context {} [executionId={}]", percentage, scaleUpContext, executionId);

      Stage resizeStage = new Stage(execution, ResizeServerGroupStage.TYPE, "Grow target to " + percentage + "% of desired size", scaleUpContext);
      resizeStage.setParentStageId(stage.getId());
      resizeStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
      stages.add(resizeStage);

      // only generate the "disable p% of traffic" stages if we have something to disable
      if (source != null) {
        stages.addAll(getBeforeCleanupStages(stage, stageData));
        Map<String, Object> scaleDownContext =
          getScalingContext(stage, cleanupConfig, baseContext, sourceCapacity, 100 - percentage, source.getServerGroupName());
        scaleDownContext.put("scaleStoppedServerGroup", true);

        log.info("Adding `Shrink source to {}% of initial size` stage with context {} [executionId={}]", 100 - percentage, scaleDownContext, executionId);

        Stage scaleDownStage = new Stage(execution, ResizeServerGroupStage.TYPE, "Shrink source to " + (100 - percentage) + "% of initial size", scaleDownContext);
        scaleDownStage.setParentStageId(stage.getId());
        scaleDownStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
        stages.add(scaleDownStage);
      }
    }

    if (source != null) {
      // shrink cluster to size
      Map<String, Object> shrinkClusterContext = new HashMap<>(baseContext);
      shrinkClusterContext.put("allowDeleteActive", false);
      shrinkClusterContext.put("shrinkToSize", stage.getContext().get("maxRemainingAsgs"));
      shrinkClusterContext.put("retainLargerOverNewer", false);
      Stage shrinkClusterStage = new Stage(execution, ShrinkClusterStage.STAGE_TYPE, "shrinkCluster", shrinkClusterContext);
      shrinkClusterStage.setParentStageId(stage.getId());
      shrinkClusterStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
      stages.add(shrinkClusterStage);

      // disable old
      log.info("Adding `Disable cluster` stage with context {} [executionId={}]", baseContext, executionId);
      Map<String, Object> disableContext = new HashMap<>(baseContext);
      Stage disableStage = new Stage(execution, DisableClusterStage.STAGE_TYPE, "Disable cluster", disableContext);
      disableStage.setParentStageId(stage.getId());
      disableStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
      stages.add(disableStage);

      // scale old back to original
      Map<String, Object> scaleSourceContext = getScalingContext(stage, cleanupConfig, baseContext, sourceCapacity, 100, source.getServerGroupName());
      scaleSourceContext.put("scaleStoppedServerGroup", true);
      log.info("Adding `Grow source to 100% of original size` stage with context {} [executionId={}]", scaleSourceContext, executionId);
      Stage scaleSourceStage = new Stage(execution, ResizeServerGroupStage.TYPE, "Reset source to original size", scaleSourceContext);
      scaleSourceStage.setParentStageId(stage.getId());
      scaleSourceStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
      stages.add(scaleSourceStage);

      if (stageData.getDelayBeforeScaleDown() > 0L) {
        Map<String, Object> waitContext = Collections.singletonMap("waitTime", stageData.getDelayBeforeScaleDown());
        Stage delayStage = new Stage(execution, WaitStage.STAGE_TYPE, "Wait Before Scale Down", waitContext);
        delayStage.setParentStageId(stage.getId());
        delayStage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
        stages.add(delayStage);
      }
    }

    return stages;
  }

  private List<Stage> getBeforeCleanupStages(Stage parentStage,
                                             RollingRedBlackStageData stageData) {
    List<Stage> stages = new ArrayList<>();

    if (stageData.getDelayBeforeCleanup() != 0) {
      Map<String, Object> waitContext = Collections.singletonMap("waitTime", stageData.getDelayBeforeCleanup());
      Stage stage = new Stage(parentStage.getExecution(), WaitStage.STAGE_TYPE, "wait", waitContext);
      stage.setParentStageId(parentStage.getId());
      stage.setSyntheticStageOwner(SyntheticStageOwner.STAGE_AFTER);
      stages.add(stage);
    }

    return stages;
  }

  private Map<String, Object> getScalingContext(Stage stage,
                                                AbstractDeployStrategyStage.CleanupConfig cleanupConfig,
                                                Map<String, Object> baseContext,
                                                ResizeStrategy.Capacity savedCapacity,
                                                Integer percentage,
                                                @Nullable String serverGroupName) {
    Map<String, Object> scaleContext = new HashMap<>(baseContext);
    if (serverGroupName != null) {
      scaleContext.put("serverGroupName", serverGroupName);
    } else {
      scaleContext.put("target", TargetServerGroup.Params.Target.current_asg_dynamic);
    }
    scaleContext.put("targetLocation", cleanupConfig.getLocation());
    scaleContext.put("scalePct", percentage);
    scaleContext.put("pinCapacity", percentage < 100); // if p < 100, capacity should be pinned (min == max == desired)
    scaleContext.put("unpinMinimumCapacity", percentage == 100); // if p == 100, min capacity should be restored to the original unpinned value from source
    scaleContext.put("useNameAsLabel", true); // hint to deck that it should _not_ override the name
    scaleContext.put("targetHealthyDeployPercentage", stage.getContext().get("targetHealthyDeployPercentage"));
    scaleContext.put("action", ResizeStrategy.ResizeAction.scale_exact);
    scaleContext.put("capacity", savedCapacity); // we always scale to what was part of the manifest configuration

    return scaleContext;
  }
}
