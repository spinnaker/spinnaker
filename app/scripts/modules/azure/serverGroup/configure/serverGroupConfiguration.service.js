'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.service', [
  require('../../image/image.reader.js'),
  require('../../../core/account/account.service.js'),
  require('../../../netflix/serverGroup/diff/diff.service.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../amazon/instance/awsInstanceType.service.js'),
  require('../../subnet/subnet.read.service.js'),
  require('../../keyPairs/keyPairs.read.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('azureServerGroupConfigurationService', function($q, azureImageReader, accountService, securityGroupReader,
                                                          azureInstanceTypeService, cacheInitializer,
                                                          diffService, namingService,
                                                          subnetReader, keyPairsReader, loadBalancerReader, _) {


    var healthCheckTypes = ['EC2', 'ELB'],
      terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

    function configureUpdateCommand(command) {
      command.backingData = {
        healthCheckTypes: angular.copy(healthCheckTypes),
        terminationPolicies: angular.copy(terminationPolicies)
      };
    }

    function configureCommand(application, command) {
      return $q.all({
        credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('azure'),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        loadBalancers: loadBalancerReader.loadLoadBalancers(application.name),
        //instanceTypes: azureInstanceTypeService.getAllTypesByRegion(),
      }).then(function(backingData) {
        var loadBalancerReloader = $q.when(null);
        backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        command.backingData = backingData;

        if (command.loadBalancers && command.loadBalancers.length) {
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

    function configureInstanceTypes(command) {
      var result = { dirty: {} };
      if (command.region) {
        var filtered = azureInstanceTypeService.getAvailableTypesForRegions(command.backingData.instanceTypes, [command.region]);
        if (command.instanceType && filtered.indexOf(command.instanceType) === -1) {
          command.instanceType = null;
          result.dirty.instanceType = true;
        }
        command.backingData.filtered.instanceTypes = filtered;
      } else {
        command.backingData.filtered.instanceTypes = [];
      }
      return result;
    }

    function configureImages(command) {
      var result = { dirty: {} };
      var regionalImages = null;
      if (command.viewState.disableImageSelection) {
        return result;
      }
      if (command.region) {
        regionalImages = command.backingData.packageImages.
          filter(function (image) {
            return image.amis && image.amis[command.region];
          }).
          map(function (image) {
            return { imageName: image.imageName, ami: image.amis ? image.amis[command.region][0] : null };
          });
        if (command.amiName && !regionalImages.some(function (image) {
          return image.imageName === command.amiName;
        })) {
          result.dirty.amiName = true;
          command.amiName = null;
        }
      } else {
        command.amiName = null;
      }
      command.backingData.filtered.images = regionalImages;
      return result;
    }

    function configureAvailabilityZones(command) {
      command.backingData.filtered.availabilityZones =
        _.find(command.backingData.credentialsKeyedByAccount[command.credentials].regions, {name: command.region}).availabilityZones;
    }

    function configureSubnetPurposes(command) {
      var result = { dirty: {} };
      var filteredData = command.backingData.filtered;
      if (command.region === null) {
        return result;
      }
      filteredData.subnetPurposes = _(command.backingData.subnets)
        .filter({account: command.credentials, region: command.region})
        .reject({target: 'elb'})
        .reject({purpose: null})
        .uniq('purpose')
        .valueOf();

      if (!_(filteredData.subnetPurposes).some({purpose: command.subnetType})) {
        command.subnetType = null;
        result.dirty.subnetType = true;
      }
      return result;
    }

    function getRegionalSecurityGroups(command) {
      var newSecurityGroups = command.backingData.securityGroups[command.credentials] || { azure: {}};
      return _(newSecurityGroups.azure[command.region])
        .filter({vpcId: command.vpcId || null})
        .sortBy('name')
        .valueOf();
    }

    function configureSecurityGroupOptions(command) {
      var result = { dirty: {} };
      var currentOptions = command.backingData.filtered.securityGroups;
      var newRegionalSecurityGroups = getRegionalSecurityGroups(command);
      if (currentOptions && command.securityGroups) {
        // not initializing - we are actually changing groups
        var currentGroupNames = command.securityGroups.map(function(groupId) {
          var match = _(currentOptions).find({id: groupId});
          return match ? match.name : groupId;
        });

        var matchedGroups = command.securityGroups.map(function(groupId) {
          var securityGroup = _(currentOptions).find({id: groupId}) ||
            _(currentOptions).find({name: groupId});
          return securityGroup ? securityGroup.name : null;
        }).map(function(groupName) {
          return _(newRegionalSecurityGroups).find({name: groupName});
        }).filter(function(group) {
          return group;
        });

        var matchedGroupNames = _.pluck(matchedGroups, 'name');
        var removed = _.xor(currentGroupNames, matchedGroupNames);
        command.securityGroups = _.pluck(matchedGroups, 'id');
        if (removed.length) {
          result.dirty.securityGroups = removed;
        }
      }
      command.backingData.filtered.securityGroups = newRegionalSecurityGroups;
      return result;
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

    function refreshInstanceTypes(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('instanceTypes').then(function() {
        return azureInstanceTypeService.getAllTypesByRegion().then(function(instanceTypes) {
          command.backingData.instanceTypes = instanceTypes;
          if (!skipCommandReconfiguration) {
            configureInstanceTypes(command);
          }
        });
      });
    }

    function getLoadBalancerNames(loadBalancers) {
      return _(loadBalancers)
        .pluck('name')
        .unique()
        .valueOf()
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
      return cacheInitializer.refreshCache('loadBalancers').then(function() {
        return loadBalancerReader.listLoadBalancers('azure').then(function (loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancerOptions(command);
          }
        });
      });
    }

    function configureLoadBalancers(command) {
      var result = { dirty: {} };
      var temp = command.backingData.loadBalancers;
      var filterlist = _.filter(temp, function(lb) { return (lb.account === command.credentials && lb.region === command.region);});

      command.loadBalancers = getLoadBalancerNames(filterlist);
      command.viewState.loadBalancersConfigured = true;

      return result;
    }

    function attachEventHandlers(command) {

      command.regionChanged = function regionChanged() {
        var result = { dirty: {} };
        if (command.region && command.credentials) {
          angular.extend(result.dirty, configureLoadBalancers(command).dirty);
        }

        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          var regionsForAccount = backingData.credentialsKeyedByAccount[command.credentials] || {regions: [], defaultKeyPair: null};
          backingData.filtered.regions = regionsForAccount.regions;
          if (!_(backingData.filtered.regions).some({name: command.region})) {
            command.region = null;
            result.dirty.region = true;
          } else {
            angular.extend(result.dirty, command.regionChanged().dirty);
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
      configureInstanceTypes: configureInstanceTypes,
      configureImages: configureImages,
      configureAvailabilityZones: configureAvailabilityZones,
      configureSubnetPurposes: configureSubnetPurposes,
      configureSecurityGroupOptions: configureSecurityGroupOptions,
      configureLoadBalancerOptions: configureLoadBalancerOptions,
      refreshLoadBalancers: refreshLoadBalancers,
      refreshSecurityGroups: refreshSecurityGroups,
      refreshInstanceTypes: refreshInstanceTypes,
    };


  });
