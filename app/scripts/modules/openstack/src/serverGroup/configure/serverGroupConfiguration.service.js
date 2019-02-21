'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  AccountService,
  CACHE_INITIALIZER_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  SECURITY_GROUP_READER,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.serverGroup.configure.configuration.service', [
    require('../../image/image.reader').name,
    LOAD_BALANCER_READ_SERVICE,
    SECURITY_GROUP_READER,
    CACHE_INITIALIZER_SERVICE,
  ])
  .factory('openstackServerGroupConfigurationService', ['$q', 'openstackImageReader', 'securityGroupReader', 'cacheInitializer', 'loadBalancerReader', function(
    $q,
    openstackImageReader,
    securityGroupReader,
    cacheInitializer,
    loadBalancerReader,
  ) {
    var healthCheckTypes = [],
      terminationPolicies = [
        'OldestInstance',
        'NewestInstance',
        'OldestLaunchConfiguration',
        'ClosestToNextInstanceHour',
        'Default',
      ],
      userDataTypes = ['Text', 'Swift', 'URL'];

    function configureUpdateCommand(command) {
      command.backingData = {
        healthCheckTypes: angular.copy(healthCheckTypes),
        terminationPolicies: angular.copy(terminationPolicies),
        userDataTypes: angular.copy(userDataTypes),
      };
    }

    function configureCommand(application, command) {
      return $q
        .all({
          credentialsKeyedByAccount: AccountService.getCredentialsKeyedByAccount('openstack'),
          securityGroups: securityGroupReader.getAllSecurityGroups(),
          loadBalancers: loadBalancerReader.loadLoadBalancers(application.name),
          userDataTypes: $q.when(angular.copy(userDataTypes)),
        })
        .then(function(backingData) {
          var loadBalancerReloader = $q.when(null);
          backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
          backingData.filtered = {};
          command.backingData = backingData;
          backingData.filtered.securityGroups = getRegionalSecurityGroups(command);

          if (_.get(command, 'loadBalancers').length) {
            // verify all load balancers are accounted for; otherwise, try refreshing load balancers cache
            var loadBalancerNames = getLoadBalancerNames(command.backingData.loadBalancers);
            if (_.intersection(loadBalancerNames, command.loadBalancers).length < command.loadBalancers.length) {
              loadBalancerReloader = refreshLoadBalancers(command, true);
            }
          }

          return $q.all([loadBalancerReloader]).then(function() {
            attachEventHandlers(command);
          });
        });
    }

    function configureImages(command) {
      var result = { dirty: {} };
      var regionalImages = null;
      if (command.viewState.disableImageSelection) {
        return result;
      }
      if (command.region) {
        regionalImages = command.backingData.packageImages
          .filter(function(image) {
            return image.amis && image.amis[command.region];
          })
          .map(function(image) {
            return { imageName: image.imageName, ami: image.amis ? image.amis[command.region][0] : null };
          });
        if (
          command.amiName &&
          !regionalImages.some(function(image) {
            return image.imageName === command.amiName;
          })
        ) {
          result.dirty.amiName = true;
          command.amiName = null;
        }
      } else {
        command.amiName = null;
      }
      command.backingData.filtered.images = regionalImages;
      return result;
    }

    function getRegionalSecurityGroups(command) {
      var newSecurityGroups = _.get(command, ['backingData', 'securityGroups', command.credentials, 'openstack'], {});
      return _.chain(newSecurityGroups[command.region])
        .sortBy('name')
        .value();
    }

    function configureSecurityGroupOptions(command) {
      var result = { dirty: {} };
      var currentOptions;
      if (command.backingData.filtered.securityGroups) {
        currentOptions = command.backingData.filtered.securityGroups;
      }
      var newRegionalSecurityGroups = getRegionalSecurityGroups(command);
      if (command.selectedSecurityGroup) {
        // one has not been previously selected. We are either configuring for the
        //first time or they changed regions or account
        command.selectedSecurityGroup = null;
        result.dirty.securityGroups = true;
      }
      if (currentOptions != newRegionalSecurityGroups) {
        command.backingData.filtered.securityGroups = newRegionalSecurityGroups;
        result.dirty.securityGroups = true;
      }

      if (command.backingData.filtered.securityGroups === []) {
        command.viewState.securityGroupsConfigured = false;
      } else {
        command.viewState.securityGroupsConfigured = true;
      }

      return result;
    }

    function refreshSecurityGroups(command, skipCommandReconfiguration) {
      return $q
        .when()
        .then(cacheInitializer.refreshCache('securityGroups'))
        .then(securityGroupReader.getAllSecurityGroups())
        .then(function(securityGroups) {
          command.backingData.securityGroups = securityGroups;
          if (!skipCommandReconfiguration) {
            configureSecurityGroupOptions(command);
          }
        });
    }

    function getLoadBalancerNames(loadBalancers) {
      return _.chain(loadBalancers)
        .map('name')
        .uniq()
        .value()
        .sort();
    }

    function configureLoadBalancerOptions(command) {
      var result = { dirty: {} };
      var current = command.loadBalancers;
      var newLoadBalancers = getLoadBalancerNames(command.backingData.loadBalancers);

      if (current && command.loadBalancers) {
        var matched = _.intersection(newLoadBalancers, command.loadBalancers);
        var removed = _.xor(matched, current);
        command.loadBalancers = matched;
        if (removed.length) {
          result.dirty.loadBalancers = removed;
        }
      }
      command.backingData.filtered.loadBalancers = newLoadBalancers;
      return result;
    }

    function refreshLoadBalancers(command, skipCommandReconfiguration) {
      return $q
        .when()
        .then(cacheInitializer.refreshCache('loadBalancers'))
        .then(loadBalancerReader.listLoadBalancers('openstack'))
        .then(function(loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancerOptions(command);
          }
        });
    }

    function configureLoadBalancers(command) {
      var result = { dirty: {} };
      var temp = command.backingData.loadBalancers;
      var filterlist = _.filter(temp, function(lb) {
        return lb.account === command.credentials && lb.region === command.region;
      });

      command.loadBalancers = getLoadBalancerNames(filterlist);
      command.viewState.loadBalancersConfigured = true;

      return result;
    }

    function attachEventHandlers(cmd) {
      cmd.regionChanged = function regionChanged(command) {
        var result = { dirty: {} };
        if (command.region && command.credentials) {
          angular.extend(result.dirty, configureLoadBalancers(command).dirty);
          angular.extend(result.dirty, configureSecurityGroupOptions(command).dirty);
        }

        return result;
      };

      cmd.credentialsChanged = function credentialsChanged(command) {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          var regionsForAccount = backingData.credentialsKeyedByAccount[command.credentials] || {
            regions: [],
            defaultKeyPair: null,
          };
          backingData.filtered.regions = regionsForAccount.regions;
          if (
            !_.chain(backingData.filtered.regions)
              .some({ name: command.region })
              .value()
          ) {
            command.region = null;
            result.dirty.region = true;
          } else {
            angular.extend(result.dirty, command.regionChanged(command).dirty);
          }
          if (command.region) {
            angular.extend(result.dirty, configureLoadBalancers(command).dirty);
          }
        } else {
          command.region = null;
        }
        return result;
      };
    }

    return {
      configureUpdateCommand: configureUpdateCommand,
      configureCommand: configureCommand,
      configureImages: configureImages,
      configureSecurityGroupOptions: configureSecurityGroupOptions,
      configureLoadBalancerOptions: configureLoadBalancerOptions,
      refreshLoadBalancers: refreshLoadBalancers,
      refreshSecurityGroups: refreshSecurityGroups,
      getRegionalSecurityGroups: getRegionalSecurityGroups,
    };
  }]);
