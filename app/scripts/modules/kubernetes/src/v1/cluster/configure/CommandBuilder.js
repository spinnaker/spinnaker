'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, ExpectedArtifactService } from '@spinnaker/core';

import { KubernetesProviderSettings } from '../../../kubernetes.settings';

export const KUBERNETES_V1_CLUSTER_CONFIGURE_COMMANDBUILDER = 'spinnaker.kubernetes.clusterCommandBuilder.service';
export const name = KUBERNETES_V1_CLUSTER_CONFIGURE_COMMANDBUILDER; // for backwards compatibility
module(KUBERNETES_V1_CLUSTER_CONFIGURE_COMMANDBUILDER, []).factory('kubernetesClusterCommandBuilder', function() {
  function attemptToSetValidAccount(application, defaultAccount, command) {
    return AccountService.listAccounts('kubernetes', 'v1').then(function(kubernetesAccounts) {
      const kubernetesAccountNames = _.map(kubernetesAccounts, 'name');
      let firstKubernetesAccount = null;

      if (application.accounts.length) {
        firstKubernetesAccount = _.find(application.accounts, function(applicationAccount) {
          return kubernetesAccountNames.includes(applicationAccount);
        });
      } else if (kubernetesAccountNames.length) {
        firstKubernetesAccount = kubernetesAccountNames[0];
      }

      const defaultAccountIsValid = defaultAccount && kubernetesAccountNames.includes(defaultAccount);

      command.account = defaultAccountIsValid
        ? defaultAccount
        : firstKubernetesAccount
        ? firstKubernetesAccount
        : 'my-account-name';
    });
  }

  function applyHealthProviders(application, command) {
    command.interestingHealthProviderNames = ['KubernetesContainer', 'KubernetesPod'];
  }

  function buildNewClusterCommand(application, defaults = {}) {
    const defaultAccount = defaults.account || KubernetesProviderSettings.defaults.account;

    const command = {
      account: defaultAccount,
      application: application.name,
      strategy: '',
      targetSize: 1,
      cloudProvider: 'kubernetes',
      selectedProvider: 'kubernetes',
      namespace: 'default',
      containers: [],
      initContainers: [],
      volumeSources: [],
      buildImageId: buildImageId,
      groupByRegistry: groupByRegistry,
      terminationGracePeriodSeconds: 30,
      viewState: {
        mode: defaults.mode || 'create',
        disableStrategySelection: true,
        useAutoscaler: false,
      },
      capacity: {
        min: 1,
        desired: 1,
        max: 1,
      },
      scalingPolicy: {
        cpuUtilization: {
          target: 40,
        },
      },
      useSourceCapacity: false,
      deployment: {
        enabled: false,
        minReadySeconds: 0,
        deploymentStrategy: {
          type: 'RollingUpdate',
          rollingUpdate: {
            maxUnavailable: 1,
            maxSurge: 1,
          },
        },
      },
    };

    applyHealthProviders(application, command);

    attemptToSetValidAccount(application, defaultAccount, command);

    return command;
  }

  function buildClusterCommandFromExisting(application, existing, mode) {
    mode = mode || 'clone';

    const command = _.cloneDeep(existing.deployDescription);

    command.groupByRegistry = groupByRegistry;
    command.cloudProvider = 'kubernetes';
    command.selectedProvider = 'kubernetes';
    command.account = existing.account;
    command.buildImageId = buildImageId;
    command.strategy = '';

    command.containers.forEach(container => {
      container.imageDescription.imageId = buildImageId(container.imageDescription);
    });

    command.initContainers.forEach(container => {
      container.imageDescription.imageId = buildImageId(container.imageDescription);
    });

    command.viewState = {
      mode: mode,
      useAutoscaler: !!command.scalingPolicy,
    };

    if (!command.capacity) {
      command.capacity = {
        min: command.targetSize,
        max: command.targetSize,
        desired: command.targetSize,
      };
    }

    if (!_.has(command, 'scalingPolicy.cpuUtilization.target')) {
      command.scalingPolicy = { cpuUtilization: { target: 40 } };
    }

    applyHealthProviders(application, command);

    return command;
  }

  function groupByRegistry(container) {
    if (container.imageDescription) {
      if (container.imageDescription.fromContext) {
        return 'Find Image Result(s)';
      } else if (container.imageDescription.fromTrigger) {
        return 'Images from Trigger(s)';
      } else if (container.imageDescription.fromArtifact) {
        return 'Images from Artifact(s)';
      } else {
        return container.imageDescription.registry;
      }
    }
  }

  function buildImageId(image) {
    if (image.fromFindImage) {
      return `${image.cluster} ${image.pattern}`;
    } else if (image.fromBake) {
      return `${image.repository} (Baked during execution)`;
    } else if (image.fromTrigger && !image.tag) {
      return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
    } else if (image.fromArtifact) {
      return `${image.name} (Artifact resolved at runtime)`;
    } else {
      if (image.registry) {
        return `${image.registry}/${image.repository}:${image.tag}`;
      } else {
        return `${image.repository}:${image.tag}`;
      }
    }
  }

  function reconcileUpstreamImages(containers, upstreamImages) {
    const getConfig = image => {
      if (image.fromContext) {
        return {
          match: other => other.fromContext && other.stageId === image.stageId,
          fieldsToCopy: matchImage => {
            const { cluster, pattern, repository } = matchImage;
            return { cluster, pattern, repository };
          },
        };
      } else if (image.fromTrigger) {
        return {
          match: other =>
            other.fromTrigger &&
            other.registry === image.registry &&
            other.repository === image.repository &&
            other.tag === image.tag,
          fieldsToCopy: () => ({}),
        };
      } else if (image.fromArtifact) {
        return {
          match: other => other.fromArtifact && other.stageId === image.stageId,
          fieldsToCopy: matchImage => {
            const { name } = matchImage;
            return { name };
          },
        };
      } else {
        return {
          skipProcessing: true,
        };
      }
    };

    const result = [];
    containers.forEach(container => {
      const imageDescription = container.imageDescription;
      const imageConfig = getConfig(imageDescription);
      if (imageConfig.skipProcessing) {
        result.push(container);
      } else {
        const matchingImage = upstreamImages.find(imageConfig.match);
        if (matchingImage) {
          Object.assign(imageDescription, imageConfig.fieldsToCopy(matchingImage));
          result.push(container);
        }
      }
    });
    return result;
  }

  function findContextImages(current, all, visited = {}) {
    // This actually indicates a loop in the stage dependencies.
    if (visited[current.refId]) {
      return [];
    } else {
      visited[current.refId] = true;
    }
    let result = [];
    if (current.type === 'findImage') {
      result.push({
        fromContext: true,
        fromFindImage: true,
        cluster: current.cluster,
        pattern: current.imageNamePattern,
        repository: current.name,
        stageId: current.refId,
      });
    } else if (current.type === 'bake') {
      result.push({
        fromContext: true,
        fromBake: true,
        repository: current.ami_name,
        organization: current.organization,
        stageId: current.refId,
      });
    }
    current.requisiteStageRefIds.forEach(function(id) {
      const next = all.find(stage => stage.refId === id);
      if (next) {
        result = result.concat(findContextImages(next, all, visited));
      }
    });

    return result;
  }

  function findTriggerImages(triggers) {
    return triggers
      .filter(trigger => {
        return trigger.type === 'docker';
      })
      .map(trigger => {
        return {
          fromTrigger: true,
          repository: trigger.repository,
          account: trigger.account,
          organization: trigger.organization,
          registry: trigger.registry,
          tag: trigger.tag,
        };
      });
  }

  function findArtifactImages(currentStage, pipeline) {
    const artifactImages = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(currentStage, pipeline)
      .filter(artifact => artifact.matchArtifact.type === 'docker/image')
      .map(artifact => ({
        fromArtifact: true,
        artifactId: artifact.id,
        name: artifact.matchArtifact.name,
      }));
    return artifactImages;
  }

  function buildNewClusterCommandForPipeline(current, pipeline) {
    let contextImages = findContextImages(current, pipeline.stages) || [];
    contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));
    contextImages = contextImages.concat(findArtifactImages(current, pipeline));
    return {
      strategy: '',
      viewState: {
        contextImages: contextImages,
        mode: 'editPipeline',
        submitButtonLabel: 'Done',
        requiresTemplateSelection: true,
        useAutoscaler: false,
      },
    };
  }

  function buildClusterCommandFromPipeline(app, originalCommand, current, pipeline) {
    const command = _.cloneDeep(originalCommand);
    let contextImages = findContextImages(current, pipeline.stages) || [];
    contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));
    contextImages = contextImages.concat(findArtifactImages(current, pipeline));
    command.containers = reconcileUpstreamImages(command.containers, contextImages);
    command.containers.map(container => {
      container.imageDescription.imageId = buildImageId(container.imageDescription);
    });
    command.groupByRegistry = groupByRegistry;
    command.buildImageId = buildImageId;
    command.strategy = command.strategy || '';
    command.selectedProvider = 'kubernetes';
    command.viewState = {
      mode: 'editPipeline',
      contextImages: contextImages,
      submitButtonLabel: 'Done',
      useAutoscaler: !!command.scalingPolicy,
    };

    if (!_.has(command, 'scalingPolicy.cpuUtilization.target')) {
      command.scalingPolicy = { cpuUtilization: { target: 40 } };
    }

    return command;
  }

  return {
    buildNewClusterCommand: buildNewClusterCommand,
    buildClusterCommandFromExisting: buildClusterCommandFromExisting,
    buildNewClusterCommandForPipeline: buildNewClusterCommandForPipeline,
    buildClusterCommandFromPipeline: buildClusterCommandFromPipeline,
    groupByRegistry: groupByRegistry,
    buildImageId: buildImageId,
  };
});
