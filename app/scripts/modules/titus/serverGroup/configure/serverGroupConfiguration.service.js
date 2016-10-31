'use strict';

import {Subject} from 'rxjs/Subject';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.configuration.service', [
  require('core/account/account.service'),
])
  .factory('titusServerGroupConfigurationService', function(accountService, $q) {


    function configureCommand(command) {
      command.onStrategyChange = function (strategy) {
        // Any strategy other than None or Custom should force traffic to be enabled
        if (strategy.key !== '' && strategy.key !== 'custom') {
          command.inService = true;
        }
      };
      command.image = command.viewState.imageId;
      return $q.all({
        credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('titus'),
        images: [],
      }).then((backingData) => {
        backingData.accounts = Object.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        backingData.filtered.regions = backingData.credentialsKeyedByAccount[command.credentials].regions;
        command.backingData = backingData;

        return $q.all([]).then(function() {
          attachEventHandlers(command);
        });
      });
    }

    function configureZones(command) {
      command.backingData.filtered.regions = Object.keys(command.backingData.credentialsKeyedByAccount[command.credentials].regions);
    }

    function attachEventHandlers(command) {
      command.viewState.accountChangedStream = new Subject();
      command.viewState.regionChangedStream = new Subject();
      command.viewState.groupsRemovedStream = new Subject();
      command.viewState.removedGroups = [];
      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        configureZones(command);
        if (command.credentials) {
          backingData.filtered.regions = backingData.credentialsKeyedByAccount[command.credentials].regions;
          if (!backingData.filtered.regions.some(r => r.name === command.region)) {
            command.region = null;
            command.regionChanged();
          }
        } else {
          command.region = null;
        }
        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);
        command.viewState.accountChangedStream.next(null);
        return result;
      };

      command.regionChanged = () => {
        command.viewState.regionChangedStream.next();
      };
    }

    return {
      configureCommand: configureCommand,
      configureZones: configureZones,
    };
  });
