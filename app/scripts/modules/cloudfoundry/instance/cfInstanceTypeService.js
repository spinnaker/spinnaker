'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.instanceType.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../core/cache/deckCacheFactory.js'),
  require('../../core/utils/lodash.js'),
])
  .factory('cfInstanceTypeService', function ($http, $q, _) {

    var cachedResult = null;

    var n1standard = {
      type: 'n1-standard',
      description: 'This family provides a balance of compute, memory, and network resources, and it is a good choice for general purpose applications.',
      storageType: 'SSD',
      instanceTypes: [
        {
          name: 'n1-standard-1',
          label: 'Small',
          cpu: 1,
          memory: 3.75,
          storage: {
            size: 20,
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
            size: 40,
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
            size: 80,
            count: 1
          },
          costFactor: 2
        },
        {
          name: 'n1-standard-8',
          label: 'XLarge',
          cpu: 8,
          memory: 30,
          storage: {
            size: 160,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-standard-16',
          label: '2XLarge',
          cpu: 16,
          memory: 60,
          storage: {
            size: 320,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-standard-32',
          helpFieldKey: 'cf.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 120,
          storage: {
            size: 640,
            count: 1
          },
          costFactor: 4
        }
      ]
    };

    var f1micro = {
      type: 'f1-micro bursting',
      description: 'This family of machine types is a good choice for small, non-resource intensive workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      storageType: 'Std',
      instanceTypes: [
        {
          name: 'f1-micro',
          label: 'Micro',
          cpu: 1,
          memory: 0.60,
          storage: {
            size: 10,
            count: 1
          },
          costFactor: 1
        },
        {
          name: 'g1-small',
          label: 'Small',
          cpu: 1,
          memory: 1.70,
          storage: {
            size: 10,
            count: 1
          },
          costFactor: 1
        }
      ]
    };

    var n1highmem = {
      type: 'n1-highmem',
      description: 'High memory machine types are ideal for tasks that require more memory relative to virtual cores. High memory machine types have 6.50GB of RAM per virtual core.',
      storageType: 'SSD',
      instanceTypes: [
        {
          name: 'n1-highmem-2',
          label: 'Medium',
          cpu: 2,
          memory: 13,
          storage: {
            size: 40,
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
            size: 320,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-highmem-32',
          helpFieldKey: 'cf.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 208,
          storage: {
            size: 640,
            count: 1
          },
          costFactor: 4
        }
      ]
    };

    var n1highcpu = {
      type: 'n1-highcpu',
      description: 'High CPU machine types are ideal for tasks that require more virtual cores relative to memory. High CPU machine types have one virtual core for every 0.90GB of RAM.',
      storageType: 'SSD',
      instanceTypes: [
        {
          name: 'n1-highcpu-2',
          label: 'Medium',
          cpu: 2,
          memory: 1.80,
          storage: {
            size: 40,
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
            size: 320,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-highcpu-32',
          helpFieldKey: 'cf.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 28.8,
          storage: {
            size: 640,
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
        families: [ n1standard ],
        icon: 'hdd'
      },
      {
        type: 'memory',
        label: 'High Memory',
        families: [ n1highmem ],
        icon: 'hdd'
      },
      {
        type: 'cpu',
        label: 'High CPU',
        families: [ n1highcpu ],
        icon: 'hdd'
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        families: [f1micro],
        icon: 'hdd'
      }
    ];

    function getCategories() {
      return $q.when(categories);
    }

    function getAllTypesByRegion() {

      if (cachedResult) {
        return $q.when(cachedResult);
      }

      var deferred = $q.defer();

      deferred.resolve(_(categories)
          .pluck('families')
          .flatten()
          .pluck('instanceTypes')
          .flatten()
          .pluck('name')
          .valueOf()
      );

      return deferred.promise;

    }

    function getAvailableTypesForRegions(availableRegions, selectedRegions) {
      if (availableRegions || selectedRegions) {
        var availableTypes = _(categories)
          .pluck('families')
          .flatten()
          .pluck('instanceTypes')
          .flatten()
          .pluck('name')
          .valueOf();

        return availableTypes.sort();
      }
      return [];
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion
    };
  }
);
