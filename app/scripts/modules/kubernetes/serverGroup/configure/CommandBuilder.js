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
        targetSize: 1,
        cloudProvider: 'kubernetes',
        selectedProvider: 'kubernetes',
        namespace: 'default',
        containers: [],
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

    function findUpstreamImages(current, all, visited = {}) {
      // This actually indicates a loop in the stage dependencies.
      if (visited[current.refId]) {
        return [];
      } else {
        visited[current.refId] = true;
      }
      let result = [];
      if (current.type === 'findImage') {
        result.push({ fromContext: true, cluster: current.cluster, pattern: current.imageNamePattern, repository: current.name });
      }
      current.requisiteStageRefIds.forEach(function(id) {
        let [next] = all.filter((stage) => stage.refId === id);
        if (next) {
          result = result.concat(findUpstreamImages(next, all, visited));
        }
      });

      return result;
    }

    function buildNewServerGroupCommandForPipeline(current, all) {
      return $q.when({
        viewState: {
          contextImages: findUpstreamImages(current, all),
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
          requiresTemplateSelection: true,
        }
      });
    }

    function groupByRegistry(container) {
      if (container.imageDescription.fromContext) {
        return 'Find Image Result(s)';
      } else {
        return container.imageDescription.registry;
      }
    }

    function buildImageId(image) {
      if (image.fromContext) {
        return `${image.cluster} ${image.pattern}`;
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
    };
  });
