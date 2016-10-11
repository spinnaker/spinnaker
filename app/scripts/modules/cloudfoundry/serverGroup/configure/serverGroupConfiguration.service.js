'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.configuration.service', [
  require('core/account/account.service.js'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/cache/cacheInitializer.js'),
  require('../../image/image.reader.js'),
  require('../../instance/cfInstanceTypeService.js'),
])
  .factory('cfServerGroupConfigurationService', function(cfImageReader, accountService, securityGroupReader,
                                                         cfInstanceTypeService, cacheInitializer,
                                                         $q) {


    function configureCommand(command) {
      return $q.all({
        credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('cf'),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        instanceTypes: cfInstanceTypeService.getAllTypesByRegion()
      }).then(function(backingData) {
        var securityGroupReloader = $q.when(null);
        backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        command.backingData = backingData;
        configureImages(command);

        if (command.securityGroups && command.securityGroups.length) {
          // Verify all security groups are accounted for; otherwise, try refreshing security groups cache.
          var securityGroupIds = _.map(getSecurityGroups(command), 'id');
          if (_.intersection(command.securityGroups, securityGroupIds).length < command.securityGroups.length) {
            securityGroupReloader = refreshSecurityGroups(command, true);
          }
        }

        return $q.all([securityGroupReloader, ]).then(function() {
          attachEventHandlers(command);
        });
      });
    }

    function configureInstanceTypes(command) {
      var result = { dirty: {} };
      if (command.region) {
        var filtered = cfInstanceTypeService.getAvailableTypesForRegions(command.backingData.instanceTypes, [command.region]);
        if (command.instanceType && !filtered.includes(command.instanceType)) {
          command.instanceType = null;
          result.dirty.instanceType = true;
        }
        command.backingData.filtered.instanceTypes = filtered;
      } else {
        command.backingData.filtered.instanceTypes = [];
      }
      return result;
    }

    function configureImages() {
      return { dirty: {} };
    }

    function getSecurityGroups(command) {
      var newSecurityGroups = command.backingData.securityGroups[command.credentials] || { cf: {}};
      newSecurityGroups = _.filter(newSecurityGroups.cf.global, function(securityGroup) {
        return securityGroup.network === command.network;
      });
      return _.chain(newSecurityGroups)
        .sortBy('name')
        .value();
    }

    function configureSecurityGroupOptions(command) {
      var results = { dirty: {} };
      var currentOptions = command.backingData.filtered.securityGroups;
      var newSecurityGroups = getSecurityGroups(command);
      if (currentOptions && command.securityGroups) {
        // not initializing - we are actually changing groups
        var currentGroupNames = command.securityGroups.map(function(groupId) {
          var match = _.chain(currentOptions).find({id: groupId}).value();
          return match ? match.name : groupId;
        });

        var matchedGroups = command.securityGroups.map(function(groupId) {
          var securityGroup = _.chain(currentOptions).find({id: groupId}).value() ||
              _.chain(currentOptions).find({name: groupId}).value();
          return securityGroup ? securityGroup.name : null;
        }).map(function(groupName) {
          return _.chain(newSecurityGroups).find({name: groupName}).value();
        }).filter(function(group) {
          return group;
        });

        var matchedGroupNames = _.map(matchedGroups, 'name');
        var removed = _.xor(currentGroupNames, matchedGroupNames);
        command.securityGroups = _.map(matchedGroups, 'id');
        if (removed.length) {
          results.dirty.securityGroups = removed;
        }
      }

      // Only include explicit security group options in the pulldown list.
      command.backingData.filtered.securityGroups = _.filter(newSecurityGroups, function(securityGroup) {
        return !_.isEmpty(securityGroup.targetTags);
      });

      // Identify implicit security groups so they can be optionally listed in a read-only state.
      command.implicitSecurityGroups = _.filter(newSecurityGroups, function(securityGroup) {
        return _.isEmpty(securityGroup.targetTags);
      });

      // Only include explicitly-selected security groups in the body of the command.
      command.securityGroups = _.difference(command.securityGroups, _.map(command.implicitSecurityGroups, 'id'));

      return results;
    }

    function refreshSecurityGroups(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
          command.backingData.securityGroups = securityGroups;
          if (!skipCommandReconfiguration) {
            configureSecurityGroupOptions(command);
          }
        });
      });
    }

    function refreshInstanceTypes(command) {
      return cacheInitializer.refreshCache('instanceTypes').then(function() {
        return cfInstanceTypeService.getAllTypesByRegion().then(function(instanceTypes) {
          command.backingData.instanceTypes = instanceTypes;
          configureInstanceTypes(command);
        });
      });
    }

    function attachEventHandlers(command) {

      command.regionChanged = function regionChanged() {
        var result = { dirty: {} };
        if (command.region) {
          angular.extend(result.dirty, configureInstanceTypes(command).dirty);
          angular.extend(result.dirty, configureImages(command).dirty);
        }

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);

        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          backingData.filtered.regions = Object.keys(backingData.credentialsKeyedByAccount[command.credentials].regions);
          if (!backingData.filtered.regions.includes(command.region)) {
            command.region = null;
            result.dirty.region = true;
          } else {
            angular.extend(result.dirty, command.regionChanged().dirty);
          }
        } else {
          command.region = null;
        }

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);

        return result;
      };

      command.networkChanged = function networkChanged() {
        var result = { dirty: {} };

        angular.extend(result.dirty, configureSecurityGroupOptions(command).dirty);

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);

        return result;
      };
    }

    return {
      configureCommand: configureCommand,
      configureInstanceTypes: configureInstanceTypes,
      configureImages: configureImages,
      refreshSecurityGroups: refreshSecurityGroups,
      refreshInstanceTypes: refreshInstanceTypes,
    };


  });
