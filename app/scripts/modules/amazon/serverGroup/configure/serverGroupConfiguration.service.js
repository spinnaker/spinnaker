'use strict';

import _ from 'lodash';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';
import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';
import {CACHE_INITIALIZER_SERVICE} from 'core/cache/cacheInitializer.service';
import {SERVER_GROUP_COMMAND_REGISTRY_PROVIDER} from 'core/serverGroup/configure/common/serverGroupCommandRegistry.provider';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.configure.service', [
  require('../../image/image.reader.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
  require('core/securityGroup/securityGroup.read.service.js'),
  SUBNET_READ_SERVICE,
  require('../../instance/awsInstanceType.service.js'),
  require('../../keyPairs/keyPairs.read.service.js'),
  LOAD_BALANCER_READ_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
  require('../details/scalingProcesses/autoScalingProcess.service.js'),
])
  .factory('awsServerGroupConfigurationService', function($q, awsImageReader, accountService, securityGroupReader,
                                                          awsInstanceTypeService, cacheInitializer, namingService,
                                                          subnetReader, keyPairsReader, loadBalancerReader,
                                                          serverGroupCommandRegistry, autoScalingProcessService) {


    var healthCheckTypes = ['EC2', 'ELB'],
      terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

    function configureUpdateCommand(command) {
      command.backingData = {
        healthCheckTypes: angular.copy(healthCheckTypes),
        terminationPolicies: angular.copy(terminationPolicies)
      };
    }

    function configureCommand(application, command) {
      applyOverrides('beforeConfiguration', command);
      var imageLoader;
      if (command.viewState.disableImageSelection) {
        imageLoader = $q.when(null);
      } else {
        imageLoader = command.viewState.imageId ? loadImagesFromAmi(command) : loadImagesFromApplicationName(application, command.selectedProvider);
      }

      command.toggleSuspendedProcess = function(process) {
        command.suspendedProcesses = command.suspendedProcesses || [];
        var processIndex = command.suspendedProcesses.indexOf(process);
        if (processIndex === -1) {
          command.suspendedProcesses.push(process);
        } else {
          command.suspendedProcesses.splice(processIndex, 1);
        }
      };

      command.processIsSuspended = function(process) {
        return command.suspendedProcesses.includes(process);
      };

      command.onStrategyChange = function (strategy) {
        // Any strategy other than None or Custom should force traffic to be enabled
        if (strategy.key !== '' && strategy.key !== 'custom') {
          command.suspendedProcesses = (command.suspendedProcesses || []).filter(p => p !== 'AddToLoadBalancer');
        }
      };

      command.regionIsDeprecated = () => {
        return _.has(command, 'backingData.filtered.regions') &&
          command.backingData.filtered.regions.some((region) => region.name === command.region && region.deprecated);
      };

      return $q.all({
        credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('aws'),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        loadBalancers: loadBalancerReader.listLoadBalancers('aws'),
        subnets: subnetReader.listSubnets(),
        preferredZones: accountService.getPreferredZonesByAccount('aws'),
        keyPairs: keyPairsReader.listKeyPairs(),
        packageImages: imageLoader,
        instanceTypes: awsInstanceTypeService.getAllTypesByRegion(),
        healthCheckTypes: $q.when(angular.copy(healthCheckTypes)),
        terminationPolicies: $q.when(angular.copy(terminationPolicies)),
      }).then(function(backingData) {
        var loadBalancerReloader = $q.when(null);
        var securityGroupReloader = $q.when(null);
        var instanceTypeReloader = $q.when(null);
        backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        backingData.scalingProcesses = autoScalingProcessService.listProcesses();
        command.backingData = backingData;
        configureVpcId(command);
        backingData.filtered.securityGroups = getRegionalSecurityGroups(command);
        if (command.viewState.disableImageSelection) {
          configureInstanceTypes(command);
        }

        if (command.loadBalancers && command.loadBalancers.length) {
          // verify all load balancers are accounted for; otherwise, try refreshing load balancers cache
          var loadBalancerNames = getLoadBalancerNames(command);
          if (_.intersection(loadBalancerNames, command.loadBalancers).length < command.loadBalancers.length) {
            loadBalancerReloader = refreshLoadBalancers(command, true);
          }
        }
        if (command.securityGroups && command.securityGroups.length) {
          var regionalSecurityGroupIds = _.map(getRegionalSecurityGroups(command), 'id');
          if (_.intersection(command.securityGroups, regionalSecurityGroupIds).length < command.securityGroups.length) {
            securityGroupReloader = refreshSecurityGroups(command, true);
          }
        }
        if (command.instanceType) {
          instanceTypeReloader = refreshInstanceTypes(command, true);
        }

        return $q.all([loadBalancerReloader, securityGroupReloader, instanceTypeReloader]).then(function() {
          applyOverrides('afterConfiguration', command);
          attachEventHandlers(command);
        });
      });
    }

    function applyOverrides(phase, command) {
      serverGroupCommandRegistry.getCommandOverrides('aws').forEach((override) => {
        if (override[phase]) {
          override[phase](command);
        }
      });
    }

    function loadImagesFromApplicationName(application, provider) {
      return awsImageReader.findImages({
        provider: provider,
        q: application.name.replace(/_/g, '[_\\-]') + '*',
      });
    }

    function loadImagesFromAmi(command) {
      return awsImageReader.getImage(command.viewState.imageId, command.region, command.credentials).then(
        function (namedImage) {
          if (!namedImage) {
            return [];
          }
          command.amiName = namedImage.imageName;

          let addDashToQuery = false;
          let packageBase = namedImage.imageName.split('_')[0];
          const parts = packageBase.split('-');
          if (parts.length > 3) {
            packageBase = parts.slice(0, -3).join('-');
            addDashToQuery = true;
          }
          if (!packageBase || packageBase.length < 3) {
            return [namedImage];
          }

          return awsImageReader.findImages({
            provider: command.selectedProvider,
            q: packageBase + (addDashToQuery ? '-*' : '*'),
          });
        },
        function() {
          return [];
        }
      );
    }

    function configureKeyPairs(command) {
      var result = { dirty: {} };
      if (command.credentials && command.region) {
        // isDefault is imperfect, since we don't know what the previous account/region was, but probably a safe bet
        var isDefault = _.some(command.backingData.credentialsKeyedByAccount, c => c.defaultKeyPair && command.keyPair && command.keyPair.indexOf(c.defaultKeyPair.replace('{{region}}', '')) === 0);
        var filtered = _.chain(command.backingData.keyPairs)
          .filter({account: command.credentials, region: command.region})
          .map('keyName')
          .value();
        if (command.keyPair && filtered.length && !filtered.includes(command.keyPair)) {
          var acct = command.backingData.credentialsKeyedByAccount[command.credentials] || {
              regions: [],
              defaultKeyPair: null
            };
          if (acct.defaultKeyPair) {
            // {{region}} is the only supported substitution pattern
            let defaultKeyPair = acct.defaultKeyPair.replace('{{region}}', command.region);
            if (isDefault && filtered.includes(defaultKeyPair)) {
              command.keyPair = defaultKeyPair;
            } else {
              command.keyPair = null;
              result.dirty.keyPair = true;
            }
          } else {
            command.keyPair = null;
            result.dirty.keyPair = true;
          }
        }
        command.backingData.filtered.keyPairs = filtered;
      } else {
        command.backingData.filtered.keyPairs = [];
      }
      return result;
    }

    function configureInstanceTypes(command) {
      var result = { dirty: {} };
      if (command.region && (command.virtualizationType || command.viewState.disableImageSelection)) {
        var filtered = awsInstanceTypeService.getAvailableTypesForRegions(command.backingData.instanceTypes, [command.region]);
        if (command.virtualizationType) {
          filtered = awsInstanceTypeService.filterInstanceTypes(filtered, command.virtualizationType, !!command.vpcId);
        }
        if (command.instanceType && !filtered.includes(command.instanceType)) {
          result.dirty.instanceType = command.instanceType;
          command.instanceType = null;
        }
        command.backingData.filtered.instanceTypes = filtered;
      } else {
        command.backingData.filtered.instanceTypes = [];
      }
      angular.extend(command.viewState.dirty, result.dirty);
      return result;
    }

    function configureImages(command) {
      var result = { dirty: {} };
      var regionalImages = null;
      if (!command.amiName) {
        command.virtualizationType = null;
      }
      if (command.viewState.disableImageSelection) {
        return result;
      }
      if (command.region) {
        regionalImages = command.backingData.packageImages
          .filter(function (image) {
            return image.amis && image.amis[command.region];
          })
          .map(function (image) {
            return {
              virtualizationType: image.attributes.virtualizationType,
              imageName: image.imageName,
              ami: image.amis ? image.amis[command.region][0] : null
            };
          });
        let match = regionalImages.find((image) => image.imageName === command.amiName);
        if (command.amiName && !match) {
          result.dirty.amiName = true;
          command.amiName = null;
        } else {
          command.virtualizationType = match ? match.virtualizationType : null;
        }
      } else {
        if (command.amiName) {
          result.dirty.amiName = true;
          command.amiName = null;
        }
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
      filteredData.subnetPurposes = _.chain(command.backingData.subnets)
        .filter({account: command.credentials, region: command.region})
        .reject({target: 'elb'})
        .reject({purpose: null})
        .uniqBy('purpose')
        .value();

      if (!_.chain(filteredData.subnetPurposes).some({purpose: command.subnetType}).value()) {
        command.subnetType = null;
        result.dirty.subnetType = true;
      }
      return result;
    }

    function getRegionalSecurityGroups(command) {
      var newSecurityGroups = command.backingData.securityGroups[command.credentials] || { aws: {}};
      return _.chain(newSecurityGroups.aws[command.region])
        .filter({vpcId: command.vpcId || null})
        .sortBy('name')
        .value();
    }

    function configureSecurityGroupOptions(command) {
      var result = { dirty: {} };
      var currentOptions = command.backingData.filtered.securityGroups;
      var newRegionalSecurityGroups = getRegionalSecurityGroups(command);
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
          return _.chain(newRegionalSecurityGroups).find({name: groupName}).value();
        }).filter(function(group) {
          return group;
        });

        var matchedGroupNames = _.map(matchedGroups, 'name');
        var removed = _.xor(currentGroupNames, matchedGroupNames);
        command.securityGroups = _.map(matchedGroups, 'id');
        if (removed.length) {
          result.dirty.securityGroups = removed;
        }
      }
      command.backingData.filtered.securityGroups = newRegionalSecurityGroups.sort((a, b) => {
        if (command.securityGroups.includes(a.id)) {
          return -1;
        }
        if (command.securityGroups.includes(b.id)) {
          return 1;
        }
        return a.name.localeCompare(b.name);
      });
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
        return awsInstanceTypeService.getAllTypesByRegion().then(function(instanceTypes) {
          command.backingData.instanceTypes = instanceTypes;
          if (!skipCommandReconfiguration) {
            configureInstanceTypes(command);
          }
        });
      });
    }

    function getLoadBalancerNames(command) {
      return _.chain(command.backingData.loadBalancers)
        .map('accounts')
        .flattenDeep()
        .filter({name: command.credentials})
        .map('regions')
        .flattenDeep()
        .filter({name: command.region})
        .map('loadBalancers')
        .flattenDeep()
        .filter({vpcId: command.vpcId})
        .map('name')
        .value()
        .sort();
    }

    function getVpcLoadBalancerNames(command) {
      return _.chain(command.backingData.loadBalancers)
        .map('accounts')
        .flattenDeep()
        .filter({name: command.credentials})
        .map('regions')
        .flattenDeep()
        .filter({name: command.region})
        .map('loadBalancers')
        .flattenDeep()
        .filter('vpcId')
        .map('name')
        .value()
        .sort();
    }

    function configureLoadBalancerOptions(command) {
      var result = { dirty: {} };
      var current = (command.loadBalancers || []).concat(command.vpcLoadBalancers || []);
      var newLoadBalancers = getLoadBalancerNames(command);
      var vpcLoadBalancers = getVpcLoadBalancerNames(command);

      if (current && command.loadBalancers) {
        var valid = command.vpcId ? newLoadBalancers : newLoadBalancers.concat(vpcLoadBalancers);
        var matched = _.intersection(valid, current);
        var removed = _.xor(matched, current);
        command.loadBalancers = _.intersection(newLoadBalancers, matched);
        if (!command.vpcId) {
          command.vpcLoadBalancers = _.intersection(vpcLoadBalancers, matched);
        } else {
          delete command.vpcLoadBalancers;
        }
        if (removed.length) {
          result.dirty.loadBalancers = removed;
        }
      }
      command.backingData.filtered.loadBalancers = newLoadBalancers;
      command.backingData.filtered.vpcLoadBalancers = vpcLoadBalancers;
      return result;
    }

    function refreshLoadBalancers(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('loadBalancers').then(function() {
        return loadBalancerReader.listLoadBalancers('aws').then(function (loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancerOptions(command);
          }
        });
      });
    }

    function configureVpcId(command) {
      var result = { dirty: {} };
      if (!command.subnetType) {
        command.vpcId = null;
        result.dirty.vpcId = true;
      } else {
        var subnet = _.chain(command.backingData.subnets)
          .find({purpose: command.subnetType, account: command.credentials, region: command.region}).value();
        command.vpcId = subnet ? subnet.vpcId : null;
      }
      angular.extend(result.dirty, configureInstanceTypes(command).dirty);
      return result;
    }

    function attachEventHandlers(command) {

      command.usePreferredZonesChanged = function usePreferredZonesChanged() {
        var currentZoneCount = command.availabilityZones ? command.availabilityZones.length : 0;
        var result = { dirty: {} };
        var preferredZonesForAccount = command.backingData.preferredZones[command.credentials];
        if (preferredZonesForAccount && preferredZonesForAccount[command.region] && command.viewState.usePreferredZones) {
          command.availabilityZones = angular.copy(preferredZonesForAccount[command.region].sort());
        } else {
          command.availabilityZones = _.intersection(command.availabilityZones, command.backingData.filtered.availabilityZones);
          var newZoneCount = command.availabilityZones ? command.availabilityZones.length : 0;
          if (currentZoneCount !== newZoneCount) {
            result.dirty.availabilityZones = true;
          }
        }
        return result;
      };

      command.subnetChanged = function subnetChanged() {
        var result = configureVpcId(command);
        angular.extend(result.dirty, configureSecurityGroupOptions(command).dirty);
        angular.extend(result.dirty, configureLoadBalancerOptions(command).dirty);
        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);
        return result;
      };

      command.regionChanged = function regionChanged() {
        var result = { dirty: {} };
        var filteredData = command.backingData.filtered;
        angular.extend(result.dirty, configureSubnetPurposes(command).dirty);
        if (command.region) {
          angular.extend(result.dirty, command.subnetChanged().dirty);
          angular.extend(result.dirty, configureInstanceTypes(command).dirty);

          configureAvailabilityZones(command);
          angular.extend(result.dirty, command.usePreferredZonesChanged().dirty);

          angular.extend(result.dirty, configureImages(command).dirty);
          angular.extend(result.dirty, configureKeyPairs(command).dirty);
        } else {
          filteredData.regionalAvailabilityZones = null;
        }

        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          var regionsForAccount = backingData.credentialsKeyedByAccount[command.credentials] || {regions: [], defaultKeyPair: null};
          backingData.filtered.regions = regionsForAccount.regions;
          if (!_.chain(backingData.filtered.regions).some({name: command.region}).value()) {
            command.region = null;
            result.dirty.region = true;
          } else {
            angular.extend(result.dirty, command.regionChanged().dirty);
          }
        } else {
          command.region = null;
        }
        return result;
      };

      command.imageChanged = () => configureInstanceTypes(command);

      applyOverrides('attachEventHandlers', command);
    }

    return {
      configureUpdateCommand: configureUpdateCommand,
      configureCommand: configureCommand,
      configureKeyPairs: configureKeyPairs,
      configureInstanceTypes: configureInstanceTypes,
      configureImages: configureImages,
      configureAvailabilityZones: configureAvailabilityZones,
      configureSubnetPurposes: configureSubnetPurposes,
      configureSecurityGroupOptions: configureSecurityGroupOptions,
      configureLoadBalancerOptions: configureLoadBalancerOptions,
      configureVpcId: configureVpcId,
      refreshLoadBalancers: refreshLoadBalancers,
      refreshSecurityGroups: refreshSecurityGroups,
      refreshInstanceTypes: refreshInstanceTypes,
    };


  });
