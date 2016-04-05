'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.serverGroupCommandBuilder.service', [
  require('../../../core/config/settings.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('kubernetesServerGroupCommandBuilder', function (settings, $q, accountService, namingService, _) {
    function attemptToSetValidAccount(application, defaultAccount, command) {
      return accountService.listAccounts('kubernetes').then(function(kubernetesAccounts) {
        var kubernetesAccountNames = _.pluck(kubernetesAccounts, 'name');
        var firstKubernetesAccount = null;

        if (application.accounts.length) {
          firstKubernetesAccount = _.find(application.accounts, function (applicationAccount) {
            return kubernetesAccountNames.indexOf(applicationAccount) !== -1;
          });
        }

        var defaultAccountIsValid = defaultAccount && kubernetesAccountNames.indexOf(defaultAccount) !== -1;

        command.account =
          defaultAccountIsValid ? defaultAccount : (firstKubernetesAccount ? firstKubernetesAccount : 'my-account-name');
      });
    }

    function buildNewServerGroupCommand(application, defaults = {}) {
      var defaultAccount = defaults.account || settings.providers.kubernetes.defaults.account;

      var command = {
        account: defaultAccount,
        application: application.name,
        strategy: '',
        targetSize: 1,
        cloudProvider: 'kubernetes',
        selectedProvider: 'kubernetes',
        namespace: 'default',
        containers: [],
        volumeSources: [],
        buildImageId: buildImageId,
        groupByRegistry: groupByRegistry,
        viewState: {
          mode: defaults.mode || 'create',
          disableStrategySelection: true,
        }
      };

      if (application && application.attributes && application.attributes.platformHealthOnly) {
        command.interestingHealthProviderNames = ['Kubernetes'];
      }

      attemptToSetValidAccount(application, defaultAccount, command);

      return $q.when(command);
    }

    function reconcileUpstreamImages(containers, upstreamImages) {
      let result = [];
      containers.forEach((container) => {
        if (container.imageDescription.fromContext) {
          let [matchingImage] = upstreamImages.filter((image) => container.imageDescription.stageId == image.stageId);
          if (matchingImage) {
            container.imageDescription.cluster = matchingImage.cluster;
            container.imageDescription.pattern = matchingImage.pattern;
            container.imageDescription.repository = matchingImage.repository;
            result.push(container);
          }
        } else if (container.imageDescription.fromTrigger) {
          let [matchingImage] = upstreamImages.filter((image) => {
            return container.imageDescription.registry === image.registry
              && container.imageDescription.repository === image.repository
              && container.imageDescription.tag === image.tag;
          });
          if (matchingImage) {
            result.push(container);
          }
        } else {
          result.push(container);
        }
      });
      return result;
    }

    function findUpstreamImages(current, all, visited = {}) {
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
          cluster: current.cluster,
          pattern: current.imageNamePattern,
          repository: current.name,
          stageId: current.refId
        });
      }
      current.requisiteStageRefIds.forEach(function(id) {
        let [next] = all.filter((stage) => stage.refId === id);
        if (next) {
          result = result.concat(findUpstreamImages(next, all, visited));
        }
      });

      return result;
    }

    function findTriggerImages(triggers) {
      return triggers.filter((trigger) => {
        return trigger.type === 'docker';
      }).map((trigger) => {
        return {
          fromTrigger: true,
          repository: trigger.repository,
          registry: trigger.registry,
          tag: trigger.tag,
        };
      });
    }

    function buildNewServerGroupCommandForPipeline(current, pipeline) {
      let contextImages = findUpstreamImages(current, pipeline.stages) || [];
      contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));
      return $q.when({
        strategy: '',
        viewState: {
          contextImages: contextImages,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
          requiresTemplateSelection: true,
        }
      });
    }

    function buildServerGroupCommandFromPipeline(app, command, current, pipeline) {
      let contextImages = findUpstreamImages(current, pipeline.stages) || [];
      contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));
      command.containers = reconcileUpstreamImages(command.containers, contextImages);
      command.containers.map((container) => {
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
      };
      return $q.when(command);
    }

    function groupByRegistry(container) {
      if (container.imageDescription.fromContext) {
        return 'Find Image Result(s)';
      } else if (container.imageDescription.fromTrigger) {
        return 'Images from Trigger(s)';
      } else {
        return container.imageDescription.registry;
      }
    }

    function buildImageId(image) {
      if (image.fromContext) {
        return `${image.cluster} ${image.pattern}`;
      } else if (image.fromTrigger && !image.tag) {
        return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
      } else {
        return `${image.registry}/${image.repository}:${image.tag}`;
      }
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      var command = serverGroup.deployDescription;

      command.groupByRegistry = groupByRegistry;
      command.cloudProvider = 'kubernetes';
      command.selectedProvider = 'kubernetes';
      command.account = serverGroup.account;
      command.buildImageId = buildImageId;
      command.strategy = '';

      command.containers.map((container) => {
        container.imageDescription.imageId = buildImageId(container.imageDescription);
      });

      command.source = {
        serverGroupName: serverGroup.name,
        asgName: serverGroup.name,
        account: serverGroup.account,
        region: serverGroup.region,
        namespace: serverGroup.region,
      };

      command.viewState = {
        mode: mode,
      };

      if (application && application.attributes && application.attributes.platformHealthOnly) {
        command.interestingHealthProviderNames = ['Kubernetes'];
      }

      return $q.when(command);
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
  });
