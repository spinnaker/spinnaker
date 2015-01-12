'use strict';

angular.module('deckApp.serverGroup.configure.gce')
  .factory('gceServerGroupConfigurationService', function(imageService, accountService, securityGroupService,
                                                          instanceTypeService,
                                                          $q, subnetReader, keyPairsReader, loadBalancerReader) {


    function configureCommand(command) {
      command.image = command.viewState.imageId;
      return $q.all({
        regionsKeyedByAccount: accountService.getRegionsKeyedByAccount(),
        loadBalancers: loadBalancerReader.listGCELoadBalancers(),
        preferredZones: accountService.getPreferredZonesByAccount(),
        instanceTypes: instanceTypeService.getAllTypesByRegion('gce'),
        images: imageService.findImages({provider: 'gce'})
      }).then(function(loader) {
        loader.accounts = _.keys(loader.regionsKeyedByAccount);
        loader.filtered = {};
        command.backingData = loader;
        configureImages(command);
        attachEventHandlers(command);
      });
    }

    function configureInstanceTypes(command) {
      var result = { dirty: {} };
      if (command.region) {
        var filtered = instanceTypeService.getAvailableTypesForRegions('gce', command.backingData.instanceTypes, [command.region]);
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
      // TODO(duftler): Dynamically populate this field with the correct set of images based on region/zone/project
      // TODO - Big one - make "account" optional in Oort endpoint
      if (command.viewState.disableImageSelection) {
        return result;
      }
      if (command.credentials !== command.viewState.lastImageAccount) {
        command.viewState.lastImageAccount = command.credentials;
        var filtered = extractFilteredImageNames(command);
        command.backingData.filtered.imageNames = filtered;
        if (filtered.indexOf(command.image) === -1) {
          command.image = null;
          result.dirty.imageName = true;
        }
        return result;
      }
    }

    function configureAvailabilityZones(command) {
      command.backingData.filtered.availabilityZones =
        _.find(command.backingData.regionsKeyedByAccount[command.credentials].regions, {name: command.region}).availabilityZones;
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
        .pluck('name')
        .unique()
        .valueOf();

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

    function extractFilteredImageNames(command) {
      return _(command.backingData.images)
        .filter({account: command.credentials})
        .pluck('imageName')
        .flatten(true)
        .unique()
        .valueOf();
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

      command.regionChanged = function regionChanged() {
        var result = { dirty: {} };
        var filteredData = command.backingData.filtered;
        if (command.region) {
          angular.extend(result.dirty, command.subnetChanged().dirty);
          angular.extend(result.dirty, configureInstanceTypes(command).dirty);

          configureAvailabilityZones(command);
          angular.extend(result.dirty, command.usePreferredZonesChanged().dirty);

          angular.extend(result.dirty, configureImages(command).dirty);
        } else {
          filteredData.regionalAvailabilityZones = null;
        }

        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          backingData.filtered.regionToZonesMap = backingData.regionsKeyedByAccount[command.credentials].regions;
          if (!backingData.filtered.regionToZonesMap) {
            // TODO(duftler): Move these default values to settings.js and/or accountService.js.
            backingData.filtered.regionToZonesMap = {
              'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-f'],
              'europe-west1': ['europe-west1-a', 'europe-west1-b', 'europe-west1-c'],
              'asia-east1': ['asia-east1-a', 'asia-east1-b', 'asia-east1-c']
            };
          }
          backingData.filtered.regions = Object.keys(backingData.filtered.regionToZonesMap);
          if (backingData.filtered.regions.indexOf(command.region) === -1) {
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

      command.transformInstanceMetadata = function() {
        var transformedInstanceMetadata = {};

        // The instanceMetadata is stored using 'key' and 'value' attributes to enable the Add/Remove behavior in the wizard.
        command.instanceMetadata.forEach(function(metadataPair) {
          transformedInstanceMetadata[metadataPair.key] = metadataPair.value;
        });

        // We use this list of load balancer names when 'Enabling' a server group.
        if (command.loadBalancers.length > 0) {
          transformedInstanceMetadata['load-balancer-names'] = command.loadBalancers.toString();
        }

        command.instanceMetadata = transformedInstanceMetadata;
      };
    }

    return {
      configureCommand: configureCommand,
      configureInstanceTypes: configureInstanceTypes,
      configureImages: configureImages,
      configureAvailabilityZones: configureAvailabilityZones,
      configureLoadBalancerOptions: configureLoadBalancerOptions,
    };


  });
