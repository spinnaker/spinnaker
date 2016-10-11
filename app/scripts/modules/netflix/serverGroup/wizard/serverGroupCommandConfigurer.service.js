'use strict';

import _ from 'lodash';

import modalWizardServiceModule from 'core/modal/wizard/v2modalWizard.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.serverGroup.configurer.service', [
    require('./../diff/diff.service.js'),
    require('core/naming/naming.service.js'),
    require('core/config/settings.js'),
    require('core/serverGroup/configure/common/serverGroupCommand.registry.js'),
    modalWizardServiceModule,
  ])
  .factory('netflixServerGroupCommandConfigurer', function(diffService, namingService, v2modalWizardService) {
    function configureSecurityGroupDiffs(command) {
      var currentOptions = command.backingData.filtered.securityGroups,
          currentSecurityGroups = command.securityGroups || [];
      var currentSecurityGroupNames = currentSecurityGroups.map(function(groupId) {
        var match = _.chain(currentOptions).find({id: groupId}).value();
        return match ? match.name : groupId;
      });
      var result = diffService.diffSecurityGroups(currentSecurityGroupNames, command.viewState.clusterDiff, command.source);
      command.viewState.securityGroupDiffs = result;
    }

    function attachEventHandlers(command) {
      command.configureSecurityGroupDiffs = function () {
        configureSecurityGroupDiffs(command);
      };

      command.clusterChanged = function clusterChanged() {
        if (!command.application) {
          return;
        }
        diffService.getClusterDiffForAccount(command.credentials,
          namingService.getClusterName(command.application, command.stack, command.freeFormDetails)).then((diff) => {
            command.viewState.clusterDiff = diff;
            configureSecurityGroupDiffs(command);
          });
      };

      let originalCredentialsChanged = command.credentialsChanged;
      command.credentialsChanged = () => {
        let result = originalCredentialsChanged();
        if (command.credentials) {
          command.clusterChanged();
        }
        return result;
      };
    }

    let addWatches = (command) => {
      return [
        {
          property: 'command.viewState.securityGroupDiffs',
          method: function(newVal) {
            if (newVal && newVal.length) {
              v2modalWizardService.markDirty('security-groups');
            }
          }
        },
        {
          property: 'command.securityGroups',
          method: command.configureSecurityGroupDiffs
        }
      ];
    };

    let beforeConfiguration = (command) => {
      command.base64UserData = null;
    };

    return {
      attachEventHandlers: attachEventHandlers,
      addWatches: addWatches,
      beforeConfiguration: beforeConfiguration,
    };

  })
  .run(function(serverGroupCommandRegistry, netflixServerGroupCommandConfigurer, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      serverGroupCommandRegistry.register('aws', netflixServerGroupCommandConfigurer);
    }
  });
