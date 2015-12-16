'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.instanceType.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../core/cache/deckCacheFactory.js'),
  require('../../core/utils/lodash.js'),
  require('../../core/config/settings.js'),
  require('../../core/cache/infrastructureCaches.js'),
])
  .factory('awsInstanceTypeService', function ($http, $q, settings, _, Restangular, infrastructureCaches) {

    var m3 = {
      type: 'm3',
      description: 'This family includes the m3 instance types and provides a balance of compute, memory, and network resources, and it is a good choice for many applications.',
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
      type: 't2',
      description: 't2 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
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
      type: 'm3',
      description: 'This family includes the m3 instance types and provides a balance of compute, memory, and network resources, and it is a good choice for many applications.',
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
      type: 'r3',
      description: 'r3 instances are optimized for memory-intensive applications and have the lowest cost per GiB of RAM among Amazon EC2 instance types.',
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
        description: 'Instances that provide a balance of compute, memory, and network resources',
        families: [ m3 ],
        icon: 'hdd'
      },
      {
        type: 'memory',
        label: 'High Memory',
        description: 'Instances that are optimized for memory-intensive applications',
        families: [ r3 ],
        icon: 'hdd'
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        description: 'Instances that provide relatively small amounts of memory and CPU power',
        families: [t2, m3micro],
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

    var getAllTypesByRegion = function getAllTypesByRegion() {
      var cached = infrastructureCaches.instanceTypes.get('aws');
      if (cached) {
        return $q.when(cached);
      }
      return Restangular.all('instanceTypes')
        .getList().then(function (types) {
          var result = _(types)
            .map(function (type) {
              return { region: type.region, account: type.account, name: type.name, key: [type.region, type.account, type.name].join(':') };
            })
            .uniq('key')
            .groupBy('region')
            .valueOf();
          infrastructureCaches.instanceTypes.put('aws', result);
          return result;
        });
    };

    function getAvailableTypesForRegions(availableRegions, selectedRegions) {
      selectedRegions = selectedRegions || [];
      var availableTypes = [];

      // prime the list of available types
      if (selectedRegions && selectedRegions.length) {
        availableTypes = _.pluck(availableRegions[selectedRegions[0]], 'name');
      }

      // this will perform an unnecessary intersection with the first region, which is fine
      selectedRegions.forEach(function(selectedRegion) {
        if (availableRegions[selectedRegion]) {
          availableTypes = _.intersection(availableTypes, _.pluck(availableRegions[selectedRegion], 'name'));
        }
      });

      return availableTypes.sort();
    }

    let families = {
      paravirtual: ['c1', 'c3', 'hi1', 'hs1', 'm1', 'm2', 'm3', 't1'],
      hvm: ['c3', 'c4', 'd2', 'i2', 'g2', 'r3', 'm3', 'm4', 't2']
    };

    function filterInstanceTypesByVirtualizationType(instanceTypes, virtualizationType) {
      return instanceTypes.filter((instanceType) => {
        let [family] = instanceType.split('.');
        return families[virtualizationType].indexOf(family) > -1;
      });
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
      filterInstanceTypesByVirtualizationType: filterInstanceTypesByVirtualizationType,
    };
  }
);
