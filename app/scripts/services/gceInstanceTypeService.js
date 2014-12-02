'use strict';


angular.module('deckApp')
  .factory('gceInstanceTypeService', function ($http, $q, settings, _, $window) {

    var cachedResult = null;

    var n1standard = {
      type: 'n1-standard',
      description: 'This family provides a balance of compute, memory, and network resources, and it is a good choice for general purpose applications.',
      instanceTypes: [
        {
          name: 'n1-standard-1',
          label: 'Small',
          cpu: 1,
          memory: 3.75,
          storage: {
            type: 'SSD',
            size: 4,
            count: 1
          },
          costFactor: 1
        },
        {
          name: 'n1-standard-2',
          label: 'Medium',
          cpu: 2,
          memory: 7.5,
          storage: {
            type: 'SSD',
            size: 32,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'n1-standard-4',
          label: 'Large',
          cpu: 4,
          memory: 15,
          storage: {
            type: 'SSD',
            size: 40,
            count: 2
          },
          costFactor: 2
        },
        {
          name: 'n1-standard-8',
          label: 'XLarge',
          cpu: 8,
          memory: 30,
          storage: {
            type: 'SSD',
            size: 80,
            count: 2
          },
          costFactor: 3
        },
        {
          name: 'n1-standard-16',
          label: '2XLarge',
          cpu: 16,
          memory: 60,
          storage: {
            type: 'SSD',
            size: 80,
            count: 2
          },
          costFactor: 3
        }
      ]
    };

    var f1micro = {
      type: 'f1-micro bursting',
      description: 'This family of machine types is a good choice for small, non-resource intensive workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      instanceTypes: [
        {
          name: 'f1-micro',
          label: 'Micro',
          cpu: 1,
          memory: 0.60,
          storage: { type: 'EBS' },
          costFactor: 1
        },
        {
          name: 'g1-small',
          label: 'Small',
          cpu: 1,
          memory: 1.70,
          storage: { type: 'EBS' },
          costFactor: 1
        }
      ]
    };

    var n1highmem = {
      type: 'n1-highmem',
      description: 'High memory machine types are ideal for tasks that require more memory relative to virtual cores. High memory machine types have 6.50GB of RAM per virtual core.',
      instanceTypes: [
        {
          name: 'n1-highmem-2',
          label: 'Medium',
          cpu: 2,
          memory: 13,
          storage: {
            type: 'SSD',
            size: 32,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'n1-highmem-4',
          label: 'Large',
          cpu: 4,
          memory: 26,
          storage: {
            type: 'SSD',
            size: 80,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'n1-highmem-8',
          label: 'XLarge',
          cpu: 8,
          memory: 52,
          storage: {
            type: 'SSD',
            size: 160,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-highmem-16',
          label: '2XLarge',
          cpu: 16,
          memory: 104,
          storage: {
            type: 'SSD',
            size: 320,
            count: 1
          },
          costFactor: 3
        }
      ]
    };

    var n1highcpu = {
      type: 'n1-highcpu',
      description: 'High CPU machine types are ideal for tasks that require more virtual cores relative to memory. High CPU machine types have one virtual core for every 0.90GB of RAM.',
      instanceTypes: [
        {
          name: 'n1-highcpu-2',
          label: 'Medium',
          cpu: 2,
          memory: 1.80,
          storage: {
            type: 'SSD',
            size: 32,
            count: 1
          },
          costFactor: 1
        },
        {
          name: 'n1-highcpu-4',
          label: 'Large',
          cpu: 4,
          memory: 3.60,
          storage: {
            type: 'SSD',
            size: 80,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'n1-highcpu-8',
          label: 'XLarge',
          cpu: 8,
          memory: 7.20,
          storage: {
            type: 'SSD',
            size: 160,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'n1-highcpu-16',
          label: '2XLarge',
          cpu: 16,
          memory: 14.4,
          storage: {
            type: 'SSD',
            size: 320,
            count: 1
          },
          costFactor: 3
        }
      ]
    };

    var categories = [
      {
        type: 'general',
        label: 'General Purpose',
        families: [ n1standard ]
      },
      {
        type: 'memory',
        label: 'High Memory',
        families: [ n1highmem ]
      },
      {
        type: 'cpu',
        label: 'High CPU',
        families: [ n1highcpu ]
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        families: [f1micro]
      }
    ];

    function getCategories() {
      return $q.when(categories);
    }

    function getAllTypesByRegion() {

      // TODO: This mostly goes away when we serve up instance types via mort
      if (cachedResult) {
        return $q.when(cachedResult);
      }

      var deferred = $q.defer();

      $window.callback = function(data) {
        var regions = data.config.regions;
        var typesByRegion = [];
        regions.forEach(function(region) {
          var sizes = [];
          region.instanceTypes.forEach(function(instanceType) {
            instanceType.sizes.forEach(function(size) {
              sizes.push(size.size);
            });
          });
          var regionName = region.region;
          if (regionName.search(/-[1-9]/) === -1) {
            regionName = regionName + '-1';
          }
          typesByRegion.push({region: regionName, sizes: sizes});
        });
        cachedResult = typesByRegion;
        deferred.resolve(typesByRegion);
      };

      $http.jsonp('http://a0.awsstatic.com/pricing/1/ec2/linux-od.min.js');

      return deferred.promise;

    }

    function getAvailableTypesForRegions(selectedRegions) {
      selectedRegions = selectedRegions || [];
      return getAllTypesByRegion().then(function(availableRegions) {
        var availableTypes = [];

        var regions = availableRegions.filter(function(region) {
          return selectedRegions.indexOf(region.region) !== -1;
        });
        if (regions.length) {
          availableTypes = regions[0].sizes;
        }

        regions.forEach(function(region) {
          availableTypes = _.intersection(availableTypes, region.sizes);
        });

        return availableTypes.sort();
      });
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion
    };
  }
);
