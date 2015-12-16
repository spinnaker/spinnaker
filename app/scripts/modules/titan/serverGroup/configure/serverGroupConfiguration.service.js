'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan.configuration.service', [
  require('../../../core/account/account.service'),
])
  .factory('titanServerGroupConfigurationService', function(accountService, $q, _) {


    function configureCommand(command) {
      command.image = command.viewState.imageId;
      return $q.all({
        regionsKeyedByAccount: accountService.getRegionsKeyedByAccount('titan'),
        images: [],
      }).then(function(backingData) {
        backingData.accounts = _.keys(backingData.regionsKeyedByAccount);
        backingData.filtered = {};
        backingData.filtered.regions = backingData.regionsKeyedByAccount[command.credentials].regions;
        command.backingData = backingData;

        return $q.all([]).then(function() {
          attachEventHandlers(command);
        });
      });
    }

    function configureZones(command) {
      command.backingData.filtered.regions = Object.keys(command.backingData.regionsKeyedByAccount[command.credentials].regions);
    }

    function attachEventHandlers(command) {

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        configureZones(command);
        if (command.credentials) {
          backingData.filtered.regions = backingData.regionsKeyedByAccount[command.credentials].regions;
          if (backingData.filtered.regions.indexOf(command.region) === -1) {
            command.region = null;
            result.dirty.region = true;
          }
        } else {
          command.region = null;
        }

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);

        return result;
      };
    }

    return {
      configureCommand: configureCommand,
      configureZones: configureZones,
    };


  });
