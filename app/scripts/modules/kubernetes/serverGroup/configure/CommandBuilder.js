'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.serverGroupCommandBuilder.service', [
  require('../../../core/config/settings.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('kubernetesServerGroupCommandBuilder', function (settings, $q, accountService, namingService, _) {
    function attemptToSetValidCredentials(application, defaultCredentials, command) {
      return accountService.listAccounts('kubernetes').then(function(kubernetesAccounts) {
        var kubernetesAccountNames = _.pluck(kubernetesAccounts, 'name');
        var firstKubernetesAccount = null;

        if (application.accounts.length) {
          firstKubernetesAccount = _.find(application.accounts, function (applicationAccount) {
            return kubernetesAccountNames.indexOf(applicationAccount) !== -1;
          });
        }

        var defaultCredentialsAreValid = defaultCredentials && kubernetesAccountNames.indexOf(defaultCredentials) !== -1;

        command.credentials =
          defaultCredentialsAreValid ? defaultCredentials : (firstKubernetesAccount ? firstKubernetesAccount : 'my-account-name');
      });
    }

    function buildNewServerGroupCommand(application, defaults = {}) {
      var defaultCredentials = defaults.account || settings.providers.kubernetes.defaults.account;

      var command = {
        credentials: defaultCredentials,
        application: application.name,
        targetSize: 1,
        cloudProvider: 'kubernetes',
        selectedProvider: 'kubernetes',
        containers: [],
        viewState: {
          instanceProfile: 'custom',
          allImageSelection: null,
          useAllImageSelection: false,
          useSimpleCapacity: true,
          usePreferredZones: true,
          listImplicitSecurityGroups: false,
          mode: defaults.mode || 'create',
          disableStrategySelection: true,
        }
      };

      if (application && application.attributes && application.attributes.platformHealthOnly) {
        command.interestingHealthProviderNames = ['Kubernetes'];
      }

      attemptToSetValidCredentials(application, defaultCredentials, command);

      return $q.when(command);
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      var serverGroupName = namingService.parseServerGroupName(serverGroup.name);

      var command = {
        application: application.name,
        stack: serverGroupName.stack,
        freeFormDetails: serverGroupName.freeFormDetails,
        credentials: serverGroup.account,
        loadBalancers: serverGroup.loadBalancers,
        securityGroups: serverGroup.securityGroups,
        targetSize: serverGroup.replicas,
        cloudProvider: 'kubernetes',
        selectedProvider: 'kubernetes',
        containers: serverGroup.containers,
        source: {
          serverGroupName: serverGroup.name,
          asgName: serverGroup.name,
          zone: serverGroup.namespace,
          account: serverGroup.account,
          region: serverGroup.namespace,
        },
        viewState: {
          allImageSelection: null,
          useAllImageSelection: false,
          useSimpleCapacity: true,
          usePreferredZones: false,
          listImplicitSecurityGroups: false,
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
    };
  });
