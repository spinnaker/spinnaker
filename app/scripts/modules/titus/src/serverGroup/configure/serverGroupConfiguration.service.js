'use strict';

const angular = require('angular');
import { Subject } from 'rxjs';

import { AccountService } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.titus.configuration.service', [])
  .factory('titusServerGroupConfigurationService', function($q) {
    function configureCommand(command) {
      command.viewState.accountChangedStream = new Subject();
      command.viewState.regionChangedStream = new Subject();
      command.viewState.groupsRemovedStream = new Subject();
      command.onStrategyChange = function(strategy) {
        // Any strategy other than None or Custom should force traffic to be enabled
        if (strategy.key !== '' && strategy.key !== 'custom') {
          command.inService = true;
        }
      };
      command.image = command.viewState.imageId;
      return $q
        .all({
          credentialsKeyedByAccount: AccountService.getCredentialsKeyedByAccount('titus'),
          images: [],
        })
        .then(backingData => {
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
      command.backingData.filtered.regions = Object.keys(
        command.backingData.credentialsKeyedByAccount[command.credentials].regions,
      );
    }

    function attachEventHandlers(cmd) {
      cmd.viewState.removedGroups = [];
      cmd.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = cmd.backingData;
        configureZones(cmd);
        if (cmd.credentials) {
          cmd.registry = backingData.credentialsKeyedByAccount[cmd.credentials].registry;
          backingData.filtered.regions = backingData.credentialsKeyedByAccount[cmd.credentials].regions;
          if (!backingData.filtered.regions.some(r => r.name === cmd.region)) {
            cmd.region = null;
            cmd.regionChanged(cmd);
          }
        } else {
          cmd.region = null;
        }
        cmd.viewState.dirty = cmd.viewState.dirty || {};
        angular.extend(cmd.viewState.dirty, result.dirty);
        cmd.viewState.accountChangedStream.next(null);
        return result;
      };

      cmd.regionChanged = command => {
        command.viewState.regionChangedStream.next();
      };
    }

    return {
      configureCommand: configureCommand,
      configureZones: configureZones,
    };
  });
