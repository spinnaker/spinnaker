'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.clusterCommandBuilder.service', [
  require('../../../core/config/settings.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('kubernetesClusterCommandBuilder', function (settings, accountService, _) {
    function attemptToSetValidAccount(application, defaultAccount, command) {
      return accountService.listAccounts('kubernetes').then(function(kubernetesAccounts) {
        var kubernetesAccountNames = _.pluck(kubernetesAccounts, 'name');
        var firstKubernetesAccount = null;

        if (application.accounts.length) {
          firstKubernetesAccount = _.find(application.accounts, function (applicationAccount) {
            return kubernetesAccountNames.indexOf(applicationAccount) !== -1;
          });
        } else if (kubernetesAccountNames.length) {
          firstKubernetesAccount = kubernetesAccountNames[0];
        }

        var defaultAccountIsValid = defaultAccount && kubernetesAccountNames.indexOf(defaultAccount) !== -1;

        command.account =
          defaultAccountIsValid ? defaultAccount : (firstKubernetesAccount ? firstKubernetesAccount : 'my-account-name');
      });
    }

    function applyHealthProviders(application, command) {
      if (application && application.attributes && application.attributes.platformHealthOnly) {
        command.interestingHealthProviderNames = ['Kubernetes'];
      }
    }

    function buildNewClusterCommand(application, defaults = {}) {
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

      applyHealthProviders(application, command);

      attemptToSetValidAccount(application, defaultAccount, command);

      return command;
    }

    function buildClusterCommandFromExisting(application, existing, mode) {
      mode = mode || 'clone';

      var command = existing.deployDescription;

      command.groupByRegistry = groupByRegistry;
      command.cloudProvider = 'kubernetes';
      command.selectedProvider = 'kubernetes';
      command.account = existing.account;
      command.buildImageId = buildImageId;
      command.strategy = '';

      command.containers.forEach((container) => {
        container.imageDescription.imageId = buildImageId(container.imageDescription);
      });

      command.viewState = {
        mode: mode,
      };

      applyHealthProviders(application, command);

      return command;
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

    return {
      buildNewClusterCommand: buildNewClusterCommand,
      buildClusterCommandFromExisting: buildClusterCommandFromExisting,
      groupByRegistry: groupByRegistry,
      buildImageId: buildImageId,
    };
  });
