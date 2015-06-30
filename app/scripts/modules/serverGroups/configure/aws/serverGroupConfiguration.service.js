'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.configure.service', [
  require('../../../../services/imageService.js'),
  require('../../../account/accountService.js'),
  require('../../../securityGroups/securityGroup.read.service.js'),
  require('../../../../services/instanceTypeService.js'),
  require('../../../subnet/subnet.read.service.js'),
  require('../../../keyPairs/keyParis.read.service.js'),
  require('../../../loadBalancers/loadBalancer.read.service.js'),
  require('../../../caches/cacheInitializer.js'),
  require('utils/lodash.js'),
])
  .factory('awsServerGroupConfigurationService', function($q, imageService, accountService, securityGroupReader,
                                                          instanceTypeService, cacheInitializer,
                                                          subnetReader, keyPairsReader, loadBalancerReader, _) {


    function configureCommand(application, command) {
      var imageLoader;
      if (command.viewState.disableImageSelection) {
        imageLoader = $q.when(null);
      } else {
        imageLoader = command.viewState.imageId ? loadImagesFromAmi(command) : loadImagesFromApplicationName(application, command.selectedProvider);
      }

      return $q.all({
        regionsKeyedByAccount: accountService.getRegionsKeyedByAccount('aws'),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        loadBalancers: loadBalancerReader.listAWSLoadBalancers(),
        subnets: subnetReader.listSubnets(),
        preferredZones: accountService.getPreferredZonesByAccount(),
        keyPairs: keyPairsReader.listKeyPairs(),
        packageImages: imageLoader,
        instanceTypes: instanceTypeService.getAllTypesByRegion(),
      }).then(function(loader) {
        loader.accounts = _.keys(loader.regionsKeyedByAccount);
        loader.filtered = {};
        command.backingData = loader;
        attachEventHandlers(command);
      });
    }

    function loadImagesFromApplicationName(application, provider) {
      return imageService.findImages({
        provider: provider,
        q: application.name.replace(/_/g, '[_\\-]') + '*',
      });
    }

    function loadImagesFromAmi(command) {
      return imageService.getAmi(command.selectedProvider, command.viewState.imageId, command.region, command.credentials).then(
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

          return imageService.findImages({
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
        var filtered = instanceTypeService.getAvailableTypesForRegions(command.selectedProvider, command.backingData.instanceTypes, [command.region]);
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
        .pluck('purpose')
        .uniq()
        .map(function(purpose) { return { purpose: purpose, label: purpose }; })
        .valueOf();

      if (!_(filteredData.subnetPurposes).some({purpose: command.subnetType})) {
        command.subnetType = null;
        result.dirty.subnetType = true;
      }
      return result;
    }

    function configureSecurityGroupOptions(command) {
      var results = { dirty: {} };
      var currentOptions = command.backingData.filtered.securityGroups;
      var newSecurityGroups = command.backingData.securityGroups[command.credentials] || { aws: {}};
      var newRegionalSecurityGroups = _(newSecurityGroups.aws[command.region])
        .filter({vpcId: command.vpcId || null})
        .sortBy('name')
        .valueOf();
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
          results.dirty.securityGroups = removed;
        }
      }
      command.backingData.filtered.securityGroups = newRegionalSecurityGroups;
      return results;
    }

    function refreshSecurityGroups(command) {
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
          command.backingData.securityGroups = securityGroups;
          configureSecurityGroupOptions(command);
        });
      });
    }

    function configureLoadBalancerOptions(command) {
      var results = { dirty: {} };
      var current = command.loadBalancers;
      var newLoadBalancers = _(command.backingData.loadBalancers)
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

      if (current && command.loadBalancers) {
        var matched = _.intersection(newLoadBalancers, command.loadBalancers);
        var removed = _.xor(matched, current);
        command.loadBalancers = matched;
        if (removed.length) {
          results.dirty.loadBalancers = removed;
        }
      }
      command.backingData.filtered.loadBalancers = newLoadBalancers;
      return results;
    }

    function refreshLoadBalancers(command) {
      return cacheInitializer.refreshCache('loadBalancers').then(function() {
        return loadBalancerReader.listAWSLoadBalancers().then(function(loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          configureLoadBalancerOptions(command);
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
        var results =configureVpcId(command);
        angular.extend(results.dirty, configureSecurityGroupOptions(command).dirty);
        angular.extend(results.dirty, configureLoadBalancerOptions(command).dirty);
        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, results.dirty);
        return results;
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
    }

    return {
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
    };


  });
