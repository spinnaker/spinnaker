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
        result.push(current.name);
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
          requiresTemplateSelection: true,
        }
      });
    }

    // Mutating map call - not the best, but a copy would be too expensive.
    function buildContainerFromExisting(container) {
      container.requests = container.resources.requests;
      container.limits = container.resources.limits;
      return container;
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      var serverGroupName = namingService.parseServerGroupName(serverGroup.name);

      var command = {
        application: application.name,
        stack: serverGroupName.stack,
        freeFormDetails: serverGroupName.freeFormDetails,
        account: serverGroup.account,
        loadBalancers: serverGroup.loadBalancers,
        securityGroups: serverGroup.securityGroups,
        targetSize: serverGroup.replicas,
        cloudProvider: 'kubernetes',
        selectedProvider: 'kubernetes',
        namespace: serverGroup.region,
        containers: _.map(serverGroup.containers, buildContainerFromExisting),
        source: {
          serverGroupName: serverGroup.name,
          asgName: serverGroup.name,
          account: serverGroup.account,
          region: serverGroup.region,
          namespace: serverGroup.region,
        },
        viewState: {
          mode: mode,
        },
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
