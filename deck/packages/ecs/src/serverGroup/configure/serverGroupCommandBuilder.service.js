import _ from 'lodash';

import { AccountService } from '@spinnaker/core';

import { EcsServerGroupConfigurationService } from './serverGroupConfiguration.service';

function reconcileUpstreamImages(image, upstreamImages) {
  if (image.fromContext) {
    const matchingImage = upstreamImages.find((otherImage) => image.stageId === otherImage.stageId);
    if (matchingImage) {
      image.cluster = matchingImage.cluster;
      image.pattern = matchingImage.pattern;
      image.repository = matchingImage.repository;
      return image;
    }
    return null;
  }
  if (image.fromTrigger) {
    const matchingImage = upstreamImages.find(
      (otherImage) =>
        image.registry === otherImage.registry &&
        image.repository === otherImage.repository &&
        image.tag === otherImage.tag,
    );
    return matchingImage ? image : null;
  }
  return image;
}

function findUpstreamImages(current, all, visited = {}) {
  if (visited[current.refId]) {
    return [];
  }
  visited[current.refId] = true;
  let result = [];
  if (current.type === 'findImageFromTags') {
    result.push({ fromContext: true, imageLabelOrSha: current.imageLabelOrSha, stageId: current.refId });
  }
  current.requisiteStageRefIds.forEach((id) => {
    const next = all.find((stage) => stage.refId === id);
    if (next) {
      result = result.concat(findUpstreamImages(next, all, visited));
    }
  });
  return result;
}

function findTriggerImages(triggers) {
  return triggers
    .filter((trigger) => trigger.type === 'docker')
    .map((trigger) => ({
      fromTrigger: true,
      repository: trigger.repository,
      account: trigger.account,
      organization: trigger.organization,
      registry: trigger.registry,
      tag: trigger.tag,
    }));
}

function buildNewServerGroupCommand(application, defaults = {}) {
  const defaultCredentials = defaults.account || application.defaultCredentials.ecs;
  const defaultRegion = defaults.region || application.defaultRegions.ecs;

  return Promise.all([
    AccountService.getAvailabilityZonesForAccountAndRegion('ecs', defaultCredentials, defaultRegion),
    AccountService.getCredentialsKeyedByAccount('ecs'),
  ]).then(([availabilityZones]) => {
    const command = {
      application: application.name,
      credentials: defaultCredentials,
      region: defaultRegion,
      strategy: '',
      capacity: { min: 1, max: 1, desired: 1 },
      launchType: 'EC2',
      healthCheckType: 'EC2',
      selectedProvider: 'ecs',
      iamRole: 'None (No IAM role)',
      dockerImageCredentialsSecret: 'None (No registry credentials)',
      availabilityZones,
      subnetType: '',
      securityGroupNames: [],
      healthCheckGracePeriodSeconds: '',
      placementConstraints: [],
      placementStrategyName: '',
      taskDefinitionArtifact: {},
      useTaskDefinitionArtifact: false,
      placementStrategySequence: [],
      serviceDiscoveryAssociations: [],
      ecsClusterName: '',
      targetGroup: '',
      copySourceScalingPoliciesAndActions: true,
      preferSourceCapacity: true,
      useSourceCapacity: true,
      enableDeploymentCircuitBreaker: false,
      viewState: {
        useAllImageSelection: false,
        useSimpleCapacity: true,
        usePreferredZones: true,
        mode: defaults.mode || 'create',
        disableStrategySelection: true,
        dirty: {},
      },
    };

    if (application.attributes?.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
      command.interestingHealthProviderNames = ['ecs'];
    }

    return command;
  });
}

function buildServerGroupCommandFromPipeline(application, originalCluster, current, pipeline) {
  const pipelineCluster = _.cloneDeep(originalCluster);
  const availabilityZonesByRegion = pipelineCluster.availabilityZones || {};
  const region = Object.keys(availabilityZonesByRegion)[0] || pipelineCluster.region || application.defaultRegions?.ecs;
  const commandOptions = { account: pipelineCluster.account, region };
  return buildNewServerGroupCommand(application, commandOptions).then((command) => {
    const zones = availabilityZonesByRegion[region] || command.availabilityZones || [];
    const usePreferredZones = zones.join(',') === (command.availabilityZones || []).join(',');
    let contextImages = findUpstreamImages(current, pipeline.stages) || [];
    contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));

    if (command.docker?.image) {
      command.docker.image = reconcileUpstreamImages(command.docker.image, contextImages);
    }

    const viewState = {
      instanceProfile: undefined,
      disableImageSelection: true,
      useSimpleCapacity:
        !!pipelineCluster.capacity &&
        pipelineCluster.capacity.min === pipelineCluster.capacity.max &&
        pipelineCluster.useSourceCapacity !== true,
      usePreferredZones,
      mode: 'editPipeline',
      submitButtonLabel: 'Done',
      templatingEnabled: true,
      existingPipelineCluster: true,
      dirty: {},
      contextImages,
      pipeline,
      currentStage: current,
    };

    pipelineCluster.strategy = pipelineCluster.strategy || '';
    return Object.assign({}, command, pipelineCluster, {
      region,
      credentials: pipelineCluster.account,
      availabilityZones: zones,
      viewState,
    });
  });
}

function buildNewServerGroupCommandForPipeline(current, pipeline) {
  let contextImages = findUpstreamImages(current, pipeline.stages) || [];
  contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));
  return Promise.resolve({
    viewState: {
      requiresTemplateSelection: true,
      overrides: { viewState: { mode: 'editPipeline', contextImages, pipeline, currentStage: current } },
    },
  });
}

function buildUpdateServerGroupCommand(serverGroup, configurationService) {
  const command = {
    type: 'modifyAsg',
    asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
    healthCheckType: serverGroup.asg?.healthCheckType,
    credentials: serverGroup.account,
  };
  configurationService.configureUpdateCommand(command);
  return command;
}

function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
  const commandOptions = { account: serverGroup.account, region: serverGroup.region };
  return buildNewServerGroupCommand(application, commandOptions).then((command) => {
    command.credentials = serverGroup.account;
    command.app = serverGroup.moniker.app;
    command.stack = serverGroup.moniker.stack;
    command.region = serverGroup.region;
    command.ecsClusterName = serverGroup.ecsCluster;
    command.capacity = serverGroup.capacity;
    command.viewState.mode = mode;
    return command;
  });
}

export class EcsServerGroupCommandBuilder {
  constructor() {
    this.configurationService = new EcsServerGroupConfigurationService();
  }

  buildNewServerGroupCommand(application, defaults) {
    return buildNewServerGroupCommand(application, defaults);
  }

  buildServerGroupCommandFromExisting(application, serverGroup, mode) {
    return buildServerGroupCommandFromExisting(application, serverGroup, mode);
  }

  buildNewServerGroupCommandForPipeline(current, pipeline) {
    return buildNewServerGroupCommandForPipeline(current, pipeline);
  }

  buildServerGroupCommandFromPipeline(application, originalCluster, current, pipeline) {
    return buildServerGroupCommandFromPipeline(application, originalCluster, current, pipeline);
  }

  buildUpdateServerGroupCommand(serverGroup) {
    return buildUpdateServerGroupCommand(serverGroup, this.configurationService);
  }
}
