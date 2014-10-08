'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('instanceTypeService', function ($http, $q, settings, _, scheduledCache, $window) {

    var m3 = {
      type: 'M3',
      description: 'This family includes the M3 instance types and provides a balance of compute, memory, and network resources, and it is a good choice for many applications.',
      instanceTypes: [
        {
          name: 'm3.medium',
          label: 'Medium',
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
          name: 'm3.large',
          label: 'Large',
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
          name: 'm3.xlarge',
          label: 'XLarge',
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
          name: 'm3.2xlarge',
          label: '2XLarge',
          cpu: 8,
          memory: 30,
          storage: {
            type: 'SSD',
            size: 80,
            count: 2
          },
          costFactor: 3
        }
      ]
    };

    var t2 = {
      type: 'T2',
      description: 'T2 instances are a good choice for workloads that don’t use the full CPU o!en or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      instanceTypes: [
        {
          name: 't2.small',
          label: 'Small',
          cpu: 1,
          memory: 2,
          storage: { type: 'EBS' },
          costFactor: 1
        },
        {
          name: 't2.medium',
          label: 'Medium',
          cpu: 2,
          memory: 4,
          storage: { type: 'EBS' },
          costFactor: 2
        }
      ]
    };

    var m3micro = {
      type: 'M3',
      description: 'This family includes the M3 instance types and provides a balance of compute, memory, and network resources, and it is a good choice for many applications.',
      instanceTypes: [
        {
          name: 'm3.medium',
          label: 'Medium',
          cpu: 1,
          memory: 3.75,
          storage: {
            type: 'SSD',
            size: 4,
            count: 1
          },
          costFactor: 1
        }
      ]
    };

    var r3 = {
      type: 'R3',
      description: 'R3 instances are optimized for memory-intensive applications and have the lowest cost per GiB of RAM among Amazon EC2 instance types.',
      instanceTypes: [
        {
          name: 'r3.large',
          label: 'Large',
          cpu: 2,
          memory: 15.25,
          storage: {
            type: 'SSD',
            size: 32,
            count: 1
          },
          costFactor: 1
        },
        {
          name: 'r3.xlarge',
          label: 'XLarge',
          cpu: 4,
          memory: 30.5,
          storage: {
            type: 'SSD',
            size: 80,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'r3.2xlarge',
          label: '2XLarge',
          cpu: 8,
          memory: 61,
          storage: {
            type: 'SSD',
            size: 160,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'r3.4xlarge',
          label: '4XLarge',
          cpu: 16,
          memory: 122,
          storage: {
            type: 'SSD',
            size: 320,
            count: 1
          },
          costFactor: 4
        }
      ]
    };

    var categories = [
      {
        type: 'general',
        label: 'General Purpose',
        families: [ m3 ]
      },
      {
        type: 'memory',
        label: 'High Memory',
        families: [ r3 ]
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        families: [t2, m3micro]
      }
    ];

    function getCategories() {
      return $q.when(categories);
    }

    function getAllTypesByRegion() {
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
        deferred.resolve(typesByRegion);
      };

      $http.jsonp('http://a0.awsstatic.com/pricing/1/ec2/linux-od.min.js', { cache: scheduledCache } );

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
