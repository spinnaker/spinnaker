'use strict';

angular.module('deckApp')
  .factory('serverGroupConfigurationService', function(imageService, accountService, securityGroupService,
                                                       instanceTypeService,
                                                       oortService, mortService, $q) {


    function configureCommand(application, command) {
      var imageLoader;
      if (command.viewState.disableImageSelection) {
        imageLoader = $q.when(null);
      } else {
        imageLoader = command.viewState.imageId ? loadImagesFromAmi(command) : loadImagesFromApplicationName(application, command.selectedProvider);
      }

      return $q.all({
        regionsKeyedByAccount: accountService.getRegionsKeyedByAccount(),
        securityGroups: securityGroupService.getAllSecurityGroups(),
        loadBalancers: oortService.listAWSLoadBalancers(),
        subnets: mortService.listSubnets(),
        preferredZones: accountService.getPreferredZonesByAccount(),
        keyPairs: mortService.listKeyPairs(),
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
      return imageService.findImages(provider, application.name);
    }

    function loadImagesFromAmi(command) {
      return imageService.getAmi(command.selectedProvider, command.viewState.imageId, command.region, command.credentials).then(
        function (namedImage) {
          command.amiName = namedImage.imageName;

          var packageRegex = /((nflx-)?\w+)-?\w+/;
          var match = packageRegex.exec(namedImage.imageName);
          var packageBase = match[1];

          return imageService.findImages(command.selectedProvider, packageBase);
        },
        function() {
          return [];
        }
      );
    }

    function configureKeyPairs(command) {
      command.backingData.filtered.keyPairs = _(command.backingData.keyPairs)
        .filter({account: command.credentials, region: command.region})
        .pluck('keyName')
        .valueOf();
    }

    function configureInstanceTypes(command) {
      var result = { dirty: {} };
      if (command.region) {
        var filtered = instanceTypeService.getAvailableTypesForRegions(command.selectedProvider, command.backingData.instanceTypes, [command.region])
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
            return { imageName: image.imageName, ami: image.amis ? image.amis[command.region][0] : null }
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
        .map(function(purpose) { return { purpose: purpose, label: purpose };})
        .valueOf();

      if (!_(filteredData.subnetPurposes).some({purpose: command.subnetType})) {
        command.subnetType = null;
        result.dirty.subnetType = true;
      }
      return result;
    }

    function configureSecurityGroupOptions(command) {
      var results = { dirty: {} };
      var current = command.securityGroups;
      var currentOptions = command.backingData.filtered.securityGroups;
      var newRegionalSecurityGroups = _(command.backingData.securityGroups[command.credentials].aws[command.region])
        .filter({vpcId: command.vpcId || null})
        .valueOf();
      if (currentOptions && command.securityGroups) {
        // not initializing - we are actually changing groups
        var matchedGroupNames = command.securityGroups.map(function(groupId) {
          var securityGroup = _(currentOptions).find({id: groupId}) ||
            _(currentOptions).find({name: groupId});
          return securityGroup ? securityGroup.name : null;
        }).map(function(groupName) {
          return _(newRegionalSecurityGroups).find({name: groupName});
        }).filter(function(group) {
          return group;
        }).map(function(group) {
          return group.id;
        });
        command.securityGroups = matchedGroupNames;
        if (current.length !== command.securityGroups.length) {
          results.dirty.securityGroups = true;
        }
      }
      command.backingData.filtered.securityGroups = newRegionalSecurityGroups;
      return results;
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
        .valueOf();

      if (current && command.loadBalancers) {
        command.loadBalancers = _.intersection(newLoadBalancers, command.loadBalancers);
        if (current.length !== command.loadBalancers.length) {
          results.dirty.loadBalancers = true;
        }
      }
      command.backingData.filtered.loadBalancers = newLoadBalancers;
      return results;
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
        if (command.viewState.usePreferredZones) {
          command.availabilityZones = command.backingData.preferredZones[command.credentials][command.region].sort();
        }
        if (!command.viewState.usePreferredZones) {
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
          configureKeyPairs(command);
        } else {
          filteredData.regionalAvailabilityZones = null;
        }

        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          backingData.filtered.regions = backingData.regionsKeyedByAccount[command.credentials].regions;
          command.keyPair = backingData.regionsKeyedByAccount[command.credentials].defaultKeyPair;
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
      }
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
    };


  });
