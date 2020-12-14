'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, SETTINGS } from '@spinnaker/core';

import { GCE_INSTANCE_TYPE_DISK_DEFAULTS } from './gceInstanceTypeDisks';

export const GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE = 'spinnaker.gce.instanceType.service';
export const name = GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE; // for backwards compatibility
module(GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE, []).factory('gceInstanceTypeService', [
  '$q',
  '$log',
  function ($q, $log) {
    const cachedResult = null;

    const n1standard = {
      type: 'n1-standard',
      description:
        'This family provides a balance of compute, memory, and network resources, and it is a good choice for general purpose applications.',
      storageType: 'SSD',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'n1-standard-1',
          label: 'Small',
          cpu: 1,
          memory: 3.75,
          costFactor: 1,
        },
        {
          name: 'n1-standard-2',
          label: 'Medium',
          cpu: 2,
          memory: 7.5,
          costFactor: 2,
        },
        {
          name: 'n1-standard-4',
          label: 'Large',
          cpu: 4,
          memory: 15,
          costFactor: 2,
        },
        {
          name: 'n1-standard-8',
          label: 'XLarge',
          cpu: 8,
          memory: 30,
          costFactor: 3,
        },
        {
          name: 'n1-standard-16',
          label: '2XLarge',
          cpu: 16,
          memory: 60,
          costFactor: 3,
        },
        {
          name: 'n1-standard-32',
          label: '4XLarge',
          cpu: 32,
          memory: 120,
          costFactor: 4,
        },
        {
          name: 'n1-standard-64',
          label: '8XLarge',
          cpu: 64,
          memory: 240,
          costFactor: 4,
        },
        {
          name: 'n1-standard-96',
          label: '12XLarge',
          cpu: 96,
          memory: 360,
          costFactor: 4,
        },
      ],
    };

    const f1micro = {
      type: 'f1-micro bursting',
      description:
        'This family of machine types is a good choice for small, non-resource intensive workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      storageType: 'Std',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'f1-micro',
          label: 'Micro',
          cpu: 1,
          memory: 0.6,
          costFactor: 1,
        },
        {
          name: 'g1-small',
          label: 'Small',
          cpu: 1,
          memory: 1.7,
          costFactor: 1,
        },
        {
          name: 'e2-micro',
          label: 'E2 Micro',
          cpu: 2,
          memory: 1,
          costFactor: 1,
        },
        {
          name: 'e2-small',
          label: 'E2 Small',
          cpu: 2,
          memory: 2,
          costFactor: 2,
        },
        {
          name: 'e2-medium',
          label: 'E2 Medium',
          cpu: 2,
          memory: 4,
          costFactor: 2,
        },
      ],
    };

    const n1highmem = {
      type: 'n1-highmem',
      description:
        'High memory machine types are ideal for tasks that require more memory relative to virtual cores. High memory machine types have 6.50GB of RAM per virtual core.',
      storageType: 'SSD',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'n1-highmem-2',
          label: 'Medium',
          cpu: 2,
          memory: 13,
          costFactor: 2,
        },
        {
          name: 'n1-highmem-4',
          label: 'Large',
          cpu: 4,
          memory: 26,
          costFactor: 2,
        },
        {
          name: 'n1-highmem-8',
          label: 'XLarge',
          cpu: 8,
          memory: 52,
          costFactor: 3,
        },
        {
          name: 'n1-highmem-16',
          label: '2XLarge',
          cpu: 16,
          memory: 104,
          costFactor: 3,
        },
        {
          name: 'n1-highmem-32',
          label: '4XLarge',
          cpu: 32,
          memory: 208,
          costFactor: 4,
        },
        {
          name: 'n1-highmem-64',
          label: '8XLarge',
          cpu: 64,
          memory: 416,
          costFactor: 4,
        },
        {
          name: 'n1-highmem-96',
          label: '12XLarge',
          cpu: 96,
          memory: 624,
          costFactor: 4,
        },
      ],
    };

    const n1highcpu = {
      type: 'n1-highcpu',
      description:
        'High CPU machine types are ideal for tasks that require more virtual cores relative to memory. High CPU machine types have one virtual core for every 0.90GB of RAM.',
      storageType: 'SSD',
      storageHelpFieldKey: 'gce.instance.storage',
      instanceTypes: [
        {
          name: 'n1-highcpu-2',
          label: 'Medium',
          cpu: 2,
          memory: 1.8,
          costFactor: 1,
        },
        {
          name: 'n1-highcpu-4',
          label: 'Large',
          cpu: 4,
          memory: 3.6,
          costFactor: 2,
        },
        {
          name: 'n1-highcpu-8',
          label: 'XLarge',
          cpu: 8,
          memory: 7.2,
          costFactor: 2,
        },
        {
          name: 'n1-highcpu-16',
          label: '2XLarge',
          cpu: 16,
          memory: 14.4,
          costFactor: 3,
        },
        {
          name: 'n1-highcpu-32',
          label: '4XLarge',
          cpu: 32,
          memory: 28.8,
          costFactor: 4,
        },
        {
          name: 'n1-highcpu-64',
          label: '8XLarge',
          cpu: 64,
          memory: 57.6,
          costFactor: 4,
        },
        {
          name: 'n1-highcpu-96',
          label: '12XLarge',
          cpu: 96,
          memory: 86.4,
          costFactor: 4,
        },
      ],
    };

    const customMachine = {
      type: 'buildCustom',
      instanceTypes: [
        {
          name: 'n1buildCustom',
          nameRegex: /custom-\d{1,2}-\d{4,6}/,
          storage: {
            localSSDSupported: true,
          },
        },
        {
          name: 'e2buildCustom',
          nameRegex: /e2-custom-(\d{1,2})-(\d{3,6})/,
          storage: {
            localSSDSupported: false,
          },
        },
        {
          name: 'otherbuildCustom',
          nameRegex: /(.*)-?custom-(\d{1,2})-(\d{3,6})/,
          storage: {
            localSSDSupported: true,
          },
        },
      ],
    };

    const categories = [
      {
        type: 'general',
        label: 'General Purpose',
        families: [n1standard],
        icon: 'hdd',
      },
      {
        type: 'memory',
        label: 'High Memory',
        families: [n1highmem],
        icon: 'hdd',
      },
      {
        type: 'cpu',
        label: 'High CPU',
        families: [n1highcpu],
        icon: 'hdd',
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        families: [f1micro],
        icon: 'hdd',
      },
      {
        type: 'custom',
        label: 'Custom Type',
        families: [],
        icon: 'asterisk',
      },
      {
        type: 'buildCustom',
        label: 'Build Custom',
        families: [customMachine],
        icon: 'wrench',
      },
    ];

    const getCategories = _.memoize(() => {
      const initializedCategories = _.cloneDeep(categories);
      return AccountService.getAllAccountDetailsForProvider('gce').then((accountDetails) => {
        // All GCE accounts have the same instance type disk defaults, so we can pick the first one.
        let instanceTypeDisks = _.get(accountDetails, '[0].instanceTypeDisks');
        if (_.isEmpty(instanceTypeDisks)) {
          instanceTypeDisks = GCE_INSTANCE_TYPE_DISK_DEFAULTS;
        }
        if (instanceTypeDisks) {
          const families = _.flatten(initializedCategories.map((category) => category.families));
          families.forEach((family) => {
            family.instanceTypes.forEach((instanceType) => {
              const diskDefaults = instanceTypeDisks.find(
                (instanceTypeDisk) => instanceTypeDisk.instanceType === instanceType.name,
              );
              if (diskDefaults) {
                const disks = diskDefaults.disks
                  .map((disk) => {
                    switch (disk.type) {
                      case 'PD_SSD':
                        return {
                          type: 'pd-ssd',
                          sizeGb: disk.sizeGb,
                        };
                      case 'PD_STANDARD':
                        return {
                          type: 'pd-standard',
                          sizeGb: disk.sizeGb,
                        };
                      case 'LOCAL_SSD':
                        return {
                          type: 'local-ssd',
                          sizeGb: 375,
                        };
                      default:
                        $log.warn(`Disk type '${disk.type}' not supported.`);
                        return null;
                    }
                  })
                  .filter((disk) => !!disk);

                let size = 0;
                let count = 0;
                if (diskDefaults.supportsLocalSSD) {
                  count = disks.filter((disk) => disk.type === 'local-ssd').length;
                  size = 375;
                } else {
                  // TODO(dpeach): This will render the disk defaults incorrectly for f1-micro and g1-small instance types
                  // if the disk defaults set in Clouddriver have different sizes. Fixing it will require updating
                  // the core instance type selector.
                  // This logic will render the count of the largest disk.
                  const persistentDisks = disks.filter((disk) => disk.type.startsWith('pd-'));
                  if (persistentDisks.length) {
                    size = persistentDisks.reduce((maxSizeGb, disk) => Math.max(maxSizeGb, disk.sizeGb), 0);
                    count = persistentDisks.filter((disk) => disk.sizeGb === size).length;
                  }
                }

                instanceType.storage = {
                  localSSDSupported: diskDefaults.supportsLocalSSD,
                  size: size,
                  count: count,
                  defaultSettings: { disks },
                };
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

      return getCategories().then((categories) => {
        return _.chain(categories)
          .map('families')
          .flatten()
          .map('instanceTypes')
          .flatten()
          .map('name')
          .filter((name) => name !== 'n1buildCustom' && name !== 'e2buildCustom' && name !== 'otherbuildCustom')
          .value();
      });
    }

    function getAvailableTypesForLocations(locationToInstanceTypesMap, selectedLocations) {
      // This function is only ever called with one location.
      const [location] = selectedLocations;
      return locationToInstanceTypesMap[location].instanceTypes;
    }

    const getAvailableTypesForRegions = getAvailableTypesForLocations;

    const resolveInstanceTypeDetails = (instanceType) => {
      return {
        name: instanceType,
        storage: Object.assign({ isDefault: true }, SETTINGS.providers.gce.defaults.instanceTypeStorage),
      };
    };

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
      getAvailableTypesForLocations: getAvailableTypesForLocations,
      resolveInstanceTypeDetails: resolveInstanceTypeDetails,
    };
  },
]);
