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
      var imageLoader;
      if (command.viewState.disableImageSelection) {
        imageLoader = $q.when(null);
      } else {
        imageLoader = command.viewState.imageId ? loadImagesFromAmi(command) : loadImagesFromApplicationName(application, command.selectedProvider);
      }

      return $q.all({
        regionsKeyedByAccount: accountService.getRegionsKeyedByAccount('azure'),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        loadBalancers: loadBalancerReader.listLoadBalancers('azure'),
        subnets: subnetReader.listSubnets(),
        preferredZones: accountService.getPreferredZonesByAccount(),
        keyPairs: keyPairsReader.listKeyPairs(),
        packageImages: imageLoader,
        instanceTypes: azureInstanceTypeService.getAllTypesByRegion(),
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
      return azureImageReader.findImages({
        provider: provider,
        q: application.name.replace(/_/g, '[_\\-]') + '*',
      });
    }

    function loadImagesFromAmi(command) {
      return azureImageReader.getImage(command.viewState.imageId, command.region, command.credentials).then(
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

          return azureImageReader.findImages({
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

    function configureSecurityGroupDiffs(command) {
      var currentOptions = command.backingData.filtered.securityGroups;
      var currentSecurityGroupNames = command.securityGroups.map(function(groupId) {
        var match = _(currentOptions).find({id: groupId});
        return match ? match.name : groupId;
      });
      var result = diffService.diffSecurityGroups(currentSecurityGroupNames, command.viewState.clusterDiff, command.source);
      command.viewState.securityGroupDiffs = result;
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
        return loadBalancerReader.listLoadBalancers('azure').then(function (loadBalancers) {
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

      command.configureSecurityGroupDiffs = function () {
        configureSecurityGroupDiffs(command);
      };

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
          command.clusterChanged();
        } else {
          command.region = null;
        }
        return result;
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
