'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.configure.service', [
  require('../../image/image.reader.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../instance/awsInstanceType.service.js'),
  require('../../subnet/subnet.read.service.js'),
  require('../../keyPairs/keyPairs.read.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/serverGroup/configure/common/serverGroupCommand.registry.js'),
])
  .factory('awsServerGroupConfigurationService', function($q, awsImageReader, accountService, securityGroupReader,
                                                          awsInstanceTypeService, cacheInitializer, namingService,
                                                          subnetReader, keyPairsReader, loadBalancerReader, _,
                                                          serverGroupCommandRegistry) {


    var healthCheckTypes = ['EC2', 'ELB'],
      terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

    function configureUpdateCommand(command) {
      command.backingData = {
        healthCheckTypes: angular.copy(healthCheckTypes),
        terminationPolicies: angular.copy(terminationPolicies)
      };
    }

    function configureCommand(application, command) {
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
        return command.suspendedProcesses.indexOf(process) !== -1;
      };

      return $q.all({
        regionsKeyedByAccount: accountService.getRegionsKeyedByAccount('aws'),
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
        backingData.accounts = _.keys(backingData.regionsKeyedByAccount);
        backingData.filtered = {};
        command.backingData = backingData;
        configureVpcId(command);

        if (command.loadBalancers && command.loadBalancers.length) {
          // verify all load balancers are accounted for; otherwise, try refreshing load balancers cache
          var loadBalancerNames = getLoadBalancerNames(command);
          if (_.intersection(loadBalancerNames, command.loadBalancers).length < command.loadBalancers.length) {
            loadBalancerReloader = refreshLoadBalancers(command, true);
          }
        }
        if (command.securityGroups && command.securityGroups.length) {
          var regionalSecurityGroupIds = _.pluck(getRegionalSecurityGroups(command), 'id');
          if (_.intersection(command.securityGroups, regionalSecurityGroupIds).length < command.securityGroups.length) {
            securityGroupReloader = refreshSecurityGroups(command, true);
          }
        }
        if (command.instanceType) {
          instanceTypeReloader = refreshInstanceTypes(command, true);
        }

        return $q.all([loadBalancerReloader, securityGroupReloader, instanceTypeReloader]).then(function() {
          attachEventHandlers(command);
        });
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

          var packageBase = namedImage.imageName.split('_')[0];
          var parts = packageBase.split('-');
          if (parts.length > 3) {
            packageBase = parts.slice(0, -3).join('-');
          }
          if (!packageBase || packageBase.length < 3) {
            return [namedImage];
          }

          return awsImageReader.findImages({
            provider: command.selectedProvider,
            q: packageBase + '-*',
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
        var filtered = _(command.backingData.keyPairs)
          .filter({account: command.credentials, region: command.region})
          .pluck('keyName')
          .valueOf();
        if (command.keyPair && filtered.indexOf(command.keyPair) === -1) {
          var acct = command.backingData.regionsKeyedByAccount[command.credentials] || {regions: [], defaultKeyPair: null};
          command.keyPair = acct.defaultKeyPair;
          // Note: this will generally be ignored, so we probably won't flag it in the UI
          result.dirty.keyPair = true;
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
          filtered = awsInstanceTypeService.filterInstanceTypesByVirtualizationType(filtered, command.virtualizationType);
        }
        if (command.instanceType && filtered.indexOf(command.instanceType) === -1) {
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
        var regionalImageMatches = regionalImages.filter((image) => image.imageName === command.amiName);
        if (command.amiName && !regionalImageMatches.length) {
          result.dirty.amiName = true;
          command.amiName = null;
        } else {
          command.virtualizationType = regionalImages.length ? regionalImages[0].virtualizationType : null;
        }
      } else {
        command.amiName = null;
      }
      command.backingData.filtered.images = regionalImages;
      return result;
    }

    function configureAvailabilityZones(command) {
      command.backingData.filtered.availabilityZones =
        _.find(command.backingData.regionsKeyedByAccount[command.credentials].regions, {name: command.region}).availabilityZones;
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
      var newSecurityGroups = command.backingData.securityGroups[command.credentials] || { aws: {}};
      return _(newSecurityGroups.aws[command.region])
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
        return awsInstanceTypeService.getAllTypesByRegion().then(function(instanceTypes) {
          command.backingData.instanceTypes = instanceTypes;
          if (!skipCommandReconfiguration) {
            configureInstanceTypes(command);
          }
        });
      });
    }

    function getLoadBalancerNames(command) {
      return _(command.backingData.loadBalancers)
        .pluck('accounts')
        .flatten(true)
        .filter({name: command.credentials})
        .pluck('regions')
        .flatten(true)
        .filter({name: command.region})
        .pluck('loadBalancers')
        .flatten(true)
        .filter({vpcId: command.vpcId})
        .pluck('name')
        .unique()
        .valueOf()
        .sort();
    }

    function configureLoadBalancerOptions(command) {
      var result = { dirty: {} };
      var current = command.loadBalancers;
      var newLoadBalancers = getLoadBalancerNames(command);

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
        var subnet = _(command.backingData.subnets)
          .find({purpose: command.subnetType, account: command.credentials, region: command.region});
        command.vpcId = subnet ? subnet.vpcId : null;
      }
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
          var regionsForAccount = backingData.regionsKeyedByAccount[command.credentials] || {regions: [], defaultKeyPair: null};
          backingData.filtered.regions = regionsForAccount.regions;
          if (!_(backingData.filtered.regions).some({name: command.region})) {
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

      serverGroupCommandRegistry.getCommandOverrides('aws').forEach((override) => {
        if (override.attachEventHandlers) {
          override.attachEventHandlers(command);
        }
      });
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
