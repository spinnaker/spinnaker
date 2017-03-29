'use strict';

import _ from 'lodash';
import {ACCOUNT_SERVICE} from 'core/account/account.service.ts';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.instanceType.service', [ACCOUNT_SERVICE])
  .factory('gceInstanceTypeService', function ($http, $q, accountService) {

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
          costFactor: 1
        },
        {
          name: 'n1-standard-2',
          label: 'Medium',
          cpu: 2,
          memory: 7.5,
          costFactor: 2
        },
        {
          name: 'n1-standard-4',
          label: 'Large',
          cpu: 4,
          memory: 15,
          costFactor: 2
        },
        {
          name: 'n1-standard-8',
          label: 'XLarge',
          cpu: 8,
          memory: 30,
          costFactor: 3
        },
        {
          name: 'n1-standard-16',
          label: '2XLarge',
          cpu: 16,
          memory: 60,
          costFactor: 3
        },
        {
          name: 'n1-standard-32',
          helpFieldKey: 'gce.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 120,
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
          costFactor: 1
        },
        {
          name: 'g1-small',
          label: 'Small',
          cpu: 1,
          memory: 1.70,
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
          costFactor: 2
        },
        {
          name: 'n1-highmem-4',
          label: 'Large',
          cpu: 4,
          memory: 26,
          costFactor: 2
        },
        {
          name: 'n1-highmem-8',
          label: 'XLarge',
          cpu: 8,
          memory: 52,
          costFactor: 3
        },
        {
          name: 'n1-highmem-16',
          label: '2XLarge',
          cpu: 16,
          memory: 104,
          costFactor: 3
        },
        {
          name: 'n1-highmem-32',
          helpFieldKey: 'gce.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 208,
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
          costFactor: 1
        },
        {
          name: 'n1-highcpu-4',
          label: 'Large',
          cpu: 4,
          memory: 3.60,
          costFactor: 2
        },
        {
          name: 'n1-highcpu-8',
          label: 'XLarge',
          cpu: 8,
          memory: 7.20,
          costFactor: 2
        },
        {
          name: 'n1-highcpu-16',
          label: '2XLarge',
          cpu: 16,
          memory: 14.4,
          costFactor: 3
        },
        {
          name: 'n1-highcpu-32',
          helpFieldKey: 'gce.instanceType.32core',
          label: '4XLarge',
          cpu: 32,
          memory: 28.8,
          costFactor: 4
        }
      ]
    };

    var customMachine = {
      type: 'buildCustom',
      instanceTypes : [
        {
          name: 'buildCustom',
          nameRegex: /custom-\d{1,2}-\d{4,6}/,
          storage: {
            localSSDSupported: true
          }
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
        families: [ f1micro ],
        icon: 'hdd'
      },
      {
        type: 'custom',
        label: 'Custom Type',
        families: [],
        icon: 'asterisk'
      },
      {
        type: 'buildCustom',
        label: 'Build Custom',
        families: [ customMachine ],
        icon: 'wrench',
      },
    ];

    let getCategories = _.memoize(() => {
      let initializedCategories = _.cloneDeep(categories);
      return accountService.getAllAccountDetailsForProvider('gce').then(accountDetails => {
        // All GCE accounts have the same instance type disk defaults, so we can pick the first one.
        let instanceTypeDisks = _.get(accountDetails, '[0].instanceTypeDisks');
        if (instanceTypeDisks) {
          let families = _.flatten(initializedCategories.map(category => category.families));
          families.forEach(family => {
            family.instanceTypes.forEach(instanceType => {
              let diskDefaults = instanceTypeDisks
                .find(instanceTypeDisk => instanceTypeDisk.instanceType === instanceType.name);
              if (diskDefaults) {
                let localSSDCount = 0, persistentDiskType, persistentDiskSizeGb;
                diskDefaults.disks.forEach(disk => {
                  switch (disk.type) {
                    case 'PD_SSD':
                      persistentDiskType = 'pd-ssd';
                      persistentDiskSizeGb = disk.sizeGb;
                      break;
                    case 'PD_STANDARD':
                      persistentDiskType = 'pd-standard';
                      persistentDiskSizeGb = disk.sizeGb;
                      break;
                    case 'LOCAL_SSD':
                      localSSDCount++;
                      break;
                    default:
                      break;
                  }

                  let size, count;
                  if (family.storageType === 'SSD') {
                    size = 375;
                    count = localSSDCount;
                  } else {
                    size = persistentDiskSizeGb;
                    count = 1;
                  }

                  instanceType.storage = {
                    localSSDSupported: diskDefaults.supportsLocalSSD,
                    size,
                    count,
                    defaultSettings: {
                      persistentDiskType,
                      persistentDiskSizeGb,
                      localSSDCount,
                    },
                  };
                });
              }
            });
          });
        }
        return initializedCategories;
      });
    });

    function getAllTypesByRegion() {

      if (cachedResult) {
        return $q.when(cachedResult);
      }

      return getCategories().then(categories => {
        return _.chain(categories)
          .map('families')
          .flatten()
          .map('instanceTypes')
          .flatten()
          .map('name')
          .filter(name => name !== 'buildCustom')
          .value();
      });
    }

    function getAvailableTypesForLocations(instanceTypes, locationToInstanceTypesMap, selectedLocations) {
      // This function is only ever called with one location.
      let [location] = selectedLocations,
        availableTypesForLocation = locationToInstanceTypesMap[location].instanceTypes;

      return _.intersection(instanceTypes, availableTypesForLocation);
    }

    let getAvailableTypesForRegions = getAvailableTypesForLocations;

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
      getAvailableTypesForLocations: getAvailableTypesForLocations,
    };
  }
);
