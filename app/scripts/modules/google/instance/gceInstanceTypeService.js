'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.instanceType.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../core/cache/deckCacheFactory.js'),
  require('../../core/utils/lodash.js'),
])
  .factory('gceInstanceTypeService', function ($http, $q, _) {

    var cachedResult = null;

    var n1standard = {
      type: 'n1-standard',
      description: 'This family provides a balance of compute, memory, and network resources, and it is a good choice for general purpose applications.',
      storageType: 'SSD',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'n1-standard-1',
          label: 'Small',
          cpu: 1,
          memory: 3.75,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-standard-32',
          helpFieldKey: 'gce.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 120,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 2
            },
            localSSDSupported: true,
            size: 375,
            count: 2
          },
          costFactor: 4
        }
      ]
    };

    var f1micro = {
      type: 'f1-micro bursting',
      description: 'This family of machine types is a good choice for small, non-resource intensive workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      storageType: 'Std',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'f1-micro',
          label: 'Micro',
          cpu: 1,
          memory: 0.60,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 0
            },
            localSSDSupported: false,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 0
            },
            localSSDSupported: false,
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
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'n1-highmem-2',
          label: 'Medium',
          cpu: 2,
          memory: 13,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-highmem-32',
          helpFieldKey: 'gce.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 208,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 2
            },
            localSSDSupported: true,
            size: 375,
            count: 2
          },
          costFactor: 4
        }
      ]
    };

    var n1highcpu = {
      type: 'n1-highcpu',
      description: 'High CPU machine types are ideal for tasks that require more virtual cores relative to memory. High CPU machine types have one virtual core for every 0.90GB of RAM.',
      storageType: 'SSD',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'n1-highcpu-2',
          label: 'Medium',
          cpu: 2,
          memory: 1.80,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
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
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 1
            },
            localSSDSupported: true,
            size: 375,
            count: 1
          },
          costFactor: 3
        },
        {
          name: 'n1-highcpu-32',
          helpFieldKey: 'gce.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 28.8,
          storage: {
            defaultSettings: {
              persistentDiskType: 'pd-ssd',
              persistentDiskSizeGb: 10,
              localSSDCount: 2
            },
            localSSDSupported: true,
            size: 375,
            count: 2
          },
          costFactor: 4
        }
      ]
    };

    var categories = [
      {
        type: 'general',
        label: 'General Purpose',
        description: 'Instances that provide a balance of compute, memory, and network resources',
        families: [ n1standard ],
        icon: 'hdd'
      },
      {
        type: 'memory',
        label: 'High Memory',
        description: 'Instances that provide more memory relative to virtual cores',
        families: [ n1highmem ],
        icon: 'hdd'
      },
      {
        type: 'cpu',
        label: 'High CPU',
        description: 'Instances that provide more virtual cores relative to memory',
        families: [ n1highcpu ],
        icon: 'hdd'
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        description: 'Instances that provide relatively small amounts of memory and CPU power',
        families: [ f1micro ],
        icon: 'hdd'
      },
      {
        type: 'custom',
        label: 'Custom Type',
        description: 'Select the instance type below.',
        families: [],
        icon: 'asterisk'
      }
    ];

    function calculateStorage(type) {
      if (!type || !type.storage) {
        return 0;
      }
      return type.storage.count * type.storage.size;
    }

    function buildStats(category) {
      var stats = {
        cpu: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE
        },
        memory: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE
        },
        storage: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE
        },
        costFactor: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE
        },
        families: []
      };

      if (category.families && category.families.length) {
        category.families.forEach(function(family) {
          stats.families.push(family.type);
          var cpuMin = _.min(family.instanceTypes, 'cpu').cpu || Number.MAX_VALUE,
              cpuMax = _.max(family.instanceTypes, 'cpu').cpu || -Number.MAX_VALUE,
              memoryMin = _.min(family.instanceTypes, 'memory').memory || Number.MAX_VALUE,
              memoryMax = _.max(family.instanceTypes, 'memory').memory || -Number.MAX_VALUE,
              storageMin = calculateStorage(_.min(family.instanceTypes, calculateStorage)) || Number.MAX_VALUE,
              storageMax = calculateStorage(_.max(family.instanceTypes, calculateStorage)) || -Number.MAX_VALUE,
              costFactorMin = _.min(family.instanceTypes, 'costFactor').costFactor || Number.MAX_VALUE,
              costFactorMax = _.max(family.instanceTypes, 'costFactor').costFactor || -Number.MAX_VALUE;

          stats.cpu.min = Math.min(stats.cpu.min, cpuMin);
          stats.cpu.max = Math.max(stats.cpu.max, cpuMax);
          stats.memory.min = Math.min(stats.memory.min, memoryMin);
          stats.memory.max = Math.max(stats.memory.max, memoryMax);
          stats.storage.min = Math.min(stats.storage.min, storageMin);
          stats.storage.max = Math.max(stats.storage.max, storageMax);
          stats.costFactor.min = Math.min(stats.costFactor.min, costFactorMin);
          stats.costFactor.max = Math.max(stats.costFactor.max, costFactorMax);
        });
      }

      return stats;
    }

    function getCategories() {
      categories.map(function(category) {
        category.stats = buildStats(category);
      });
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
