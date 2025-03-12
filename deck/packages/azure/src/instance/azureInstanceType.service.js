'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE = 'spinnaker.azure.instanceType.service';
export const name = AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE; // for backwards compatibility
module(AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE, []).factory('azureInstanceTypeService', [
  '$q',
  function ($q) {
    const B = {
      type: 'B-series',
      description:
        'The B-series burstable VMs are ideal for workloads that do not need the full performance of the CPU continuously, like web servers, small databases and development and test environments.',
      instanceTypes: [
        {
          name: 'Standard_B1ms',
          label: 'Standard_B1ms',
          cpu: 1,
          memory: 2,
          storage: {
            type: 'SSD',
            count: 2,
            size: 4,
          },
        },
        {
          name: 'Standard_B1s',
          label: 'Standard_B1s',
          cpu: 1,
          memory: 1,
          storage: {
            type: 'SSD',
            count: 2,
            size: 2,
          },
        },
        {
          name: 'Standard_B2ms',
          label: 'Standard_B2ms',
          cpu: 2,
          memory: 8,
          storage: {
            type: 'SSD',
            count: 4,
            size: 16,
          },
        },
        {
          name: 'Standard_B2s',
          label: 'Standard_B2s',
          cpu: 2,
          memory: 4,
          storage: {
            type: 'SSD',
            count: 4,
            size: 8,
          },
        },
        {
          name: 'Standard_B4ms',
          label: 'Standard_B4ms',
          cpu: 4,
          memory: 16,
          storage: {
            type: 'SSD',
            count: 8,
            size: 32,
          },
        },
        {
          name: 'Standard_B8ms',
          label: 'Standard_B8ms',
          cpu: 8,
          memory: 32,
          storage: {
            type: 'SSD',
            count: 16,
            size: 64,
          },
        },
        {
          name: 'Standard_B1ls',
          label: 'Standard_B1ls',
          cpu: 1,
          memory: 0.5,
          storage: {
            type: 'SSD',
            count: 2,
            size: 1,
          },
        },
      ],
    };

    const DSV3 = {
      type: 'Dsv3-series',
      description:
        'The Dsv3-series sizes offer a combination of vCPU, memory, and temporary storage for most production workloads.',
      instanceTypes: [
        {
          name: 'Standard_D2s_v3',
          label: 'Standard_D2s_v3',
          cpu: 2,
          memory: 8,
          storage: {
            type: 'SSD',
            count: 4,
            size: 16,
          },
        },
        {
          name: 'Standard_D4s_v3',
          label: 'Standard_D4s_v3',
          cpu: 4,
          memory: 16,
          storage: {
            type: 'SSD',
            count: 8,
            size: 32,
          },
        },
        {
          name: 'Standard_D8s_v3',
          label: 'Standard_D8s_v3',
          cpu: 8,
          memory: 32,
          storage: {
            type: 'SSD',
            count: 16,
            size: 64,
          },
        },
        {
          name: 'Standard_D16s_v3',
          label: 'Standard_D16s_v3',
          cpu: 16,
          memory: 64,
          storage: {
            type: 'SSD',
            count: 32,
            size: 128,
          },
        },
        {
          name: 'Standard_D32s_v3',
          label: 'Standard_D32s_v3',
          cpu: 32,
          memory: 128,
          storage: {
            type: 'SSD',
            count: 32,
            size: 256,
          },
        },
        {
          name: 'Standard_D64s_v3',
          label: 'Standard_D64s_v3',
          cpu: 64,
          memory: 256,
          storage: {
            type: 'SSD',
            count: 32,
            size: 512,
          },
        },
      ],
    };

    const DV3 = {
      type: 'Dv3-series',
      description:
        'The Dv3-series sizes offer a combination of vCPU, memory, and temporary storage for most production workloads.',
      instanceTypes: [
        {
          name: 'Standard_D2_v3',
          label: 'Standard_D2_v3',
          cpu: 2,
          memory: 8,
          storage: {
            type: 'SSD',
            count: 4,
            size: 50,
          },
        },
        {
          name: 'Standard_D4_v3',
          label: 'Standard_D4_v3',
          cpu: 4,
          memory: 16,
          storage: {
            type: 'SSD',
            count: 8,
            size: 100,
          },
        },
        {
          name: 'Standard_D8_v3',
          label: 'Standard_D8_v3',
          cpu: 8,
          memory: 32,
          storage: {
            type: 'SSD',
            count: 16,
            size: 200,
          },
        },
        {
          name: 'Standard_D16_v3',
          label: 'Standard_D16_v3',
          cpu: 16,
          memory: 64,
          storage: {
            type: 'SSD',
            count: 32,
            size: 400,
          },
        },
        {
          name: 'Standard_D32_v3',
          label: 'Standard_D32_v3',
          cpu: 32,
          memory: 128,
          storage: {
            type: 'SSD',
            count: 32,
            size: 800,
          },
        },
        {
          name: 'Standard_D64_v3',
          label: 'Standard_D64_v3',
          cpu: 64,
          memory: 256,
          storage: {
            type: 'SSD',
            count: 32,
            size: 1600,
          },
        },
      ],
    };

    const DSV2 = {
      type: 'DSv2-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_DS1_v2',
          label: 'Standard_DS1_v2',
          cpu: 1,
          memory: 3.5,
          storage: {
            type: 'SSD',
            count: 4,
            size: 7,
          },
        },
        {
          name: 'Standard_DS2_v2',
          label: 'Standard_DS2_v2',
          cpu: 2,
          memory: 7,
          storage: {
            type: 'SSD',
            count: 8,
            size: 14,
          },
        },
        {
          name: 'Standard_DS3_v2',
          label: 'Standard_DS3_v2',
          cpu: 4,
          memory: 14,
          storage: {
            type: 'SSD',
            count: 16,
            size: 28,
          },
        },
        {
          name: 'Standard_DS4_v2',
          label: 'Standard_DS4_v2',
          cpu: 8,
          memory: 28,
          storage: {
            type: 'SSD',
            count: 32,
            size: 56,
          },
        },
        {
          name: 'Standard_DS5_v2',
          label: 'Standard_DS5_v2',
          cpu: 16,
          memory: 56,
          storage: {
            type: 'SSD',
            count: 64,
            size: 112,
          },
        },
        {
          name: 'Standard_DS11_v2',
          label: 'Standard_DS11_v2',
          cpu: 2,
          memory: 14,
          storage: {
            type: 'SSD',
            count: 8,
            size: 28,
          },
        },
        {
          name: 'Standard_DS12_v2',
          label: 'Standard_DS12_v2',
          cpu: 4,
          memory: 28,
          storage: {
            type: 'SSD',
            count: 16,
            size: 56,
          },
        },
        {
          name: 'Standard_DS13_v2',
          label: 'Standard_DS13_v2',
          cpu: 8,
          memory: 56,
          storage: {
            type: 'SSD',
            count: 32,
            size: 112,
          },
        },
        {
          name: 'Standard_DS14_v2',
          label: 'Standard_DS14_v2',
          cpu: 16,
          memory: 112,
          storage: {
            type: 'SSD',
            count: 64,
            size: 224,
          },
        },
        {
          name: 'Standard_DS15_v2',
          label: 'Standard_DS15_v2',
          cpu: 20,
          memory: 140,
          storage: {
            type: 'SSD',
            count: 64,
            size: 280,
          },
        },
      ],
    };

    const DV2 = {
      type: 'Dv2-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_D1_v2',
          label: 'Standard_D1_v2',
          cpu: 1,
          memory: 3.5,
          storage: {
            type: 'SSD',
            count: 4,
            size: 50,
          },
        },
        {
          name: 'Standard_D2_v2',
          label: 'Standard_D2_v2',
          cpu: 2,
          memory: 7,
          storage: {
            type: 'SSD',
            count: 8,
            size: 100,
          },
        },
        {
          name: 'Standard_D3_v2',
          label: 'Standard_D3_v2',
          cpu: 4,
          memory: 14,
          storage: {
            type: 'SSD',
            count: 16,
            size: 200,
          },
        },
        {
          name: 'Standard_D4_v2',
          label: 'Standard_D4_v2',
          cpu: 8,
          memory: 28,
          storage: {
            type: 'SSD',
            count: 32,
            size: 400,
          },
        },
        {
          name: 'Standard_D5_v2',
          label: 'Standard_D5_v2',
          cpu: 16,
          memory: 56,
          storage: {
            type: 'SSD',
            count: 64,
            size: 800,
          },
        },
        {
          name: 'Standard_D11_v2',
          label: 'Standard_D11_v2',
          cpu: 2,
          memory: 14,
          storage: {
            type: 'SSD',
            count: 8,
            size: 100,
          },
        },
        {
          name: 'Standard_D12_v2',
          label: 'Standard_D12_v2',
          cpu: 4,
          memory: 28,
          storage: {
            type: 'SSD',
            count: 16,
            size: 200,
          },
        },
        {
          name: 'Standard_D13_v2',
          label: 'Standard_D13_v2',
          cpu: 8,
          memory: 56,
          storage: {
            type: 'SSD',
            count: 32,
            size: 400,
          },
        },
        {
          name: 'Standard_D14_v2',
          label: 'Standard_D14_v2',
          cpu: 16,
          memory: 112,
          storage: {
            type: 'SSD',
            count: 64,
            size: 800,
          },
        },
        {
          name: 'Standard_D15_v2',
          label: 'Standard_D15_v2',
          cpu: 20,
          memory: 140,
          storage: {
            type: 'SSD',
            count: 64,
            size: 280,
          },
        },
      ],
    };

    const AV2 = {
      type: 'Av2-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_A1_v2',
          label: 'Standard_A1_v2',
          cpu: 1,
          memory: 2,
          storage: {
            type: 'SSD',
            count: 2,
            size: 10,
          },
        },
        {
          name: 'Standard_A2m_v2',
          label: 'Standard_A2m_v2',
          cpu: 2,
          memory: 16,
          storage: {
            type: 'SSD',
            count: 4,
            size: 20,
          },
        },
        {
          name: 'Standard_A2_v2',
          label: 'Standard_A2_v2',
          cpu: 2,
          memory: 4,
          storage: {
            type: 'SSD',
            count: 4,
            size: 20,
          },
        },
        {
          name: 'Standard_A4m_v2',
          label: 'Standard_A4m_v2',
          cpu: 4,
          memory: 32,
          storage: {
            type: 'SSD',
            count: 8,
            size: 40,
          },
        },
        {
          name: 'Standard_A4_v2',
          label: 'Standard_A4_v2',
          cpu: 4,
          memory: 8,
          storage: {
            type: 'SSD',
            count: 8,
            size: 40,
          },
        },
        {
          name: 'Standard_A8m_v2',
          label: 'Standard_A8m_v2',
          cpu: 8,
          memory: 64,
          storage: {
            type: 'SSD',
            count: 16,
            size: 80,
          },
        },
        {
          name: 'Standard_A8_v2',
          label: 'Standard_A8_v2',
          cpu: 8,
          memory: 16,
          storage: {
            type: 'SSD',
            count: 16,
            size: 80,
          },
        },
      ],
    };

    const DC = {
      type: 'DC-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_DC2s',
          label: 'Standard_DC2s',
          cpu: 2,
          memory: 8,
          storage: {
            type: 'SSD',
            count: 2,
            size: 100,
          },
        },
        {
          name: 'Standard_DC4s',
          label: 'Standard_DC4s',
          cpu: 4,
          memory: 16,
          storage: {
            type: 'SSD',
            count: 4,
            size: 200,
          },
        },
      ],
    };

    const FSV2 = {
      type: 'Fsv2-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_F2s_v2',
          label: 'Standard_F2s_v2',
          cpu: 2,
          memory: 4,
          storage: { type: 'SSD', count: 4, size: 16 },
        },
        {
          name: 'Standard_F4s_v2',
          label: 'Standard_F4s_v2',
          cpu: 4,
          memory: 8,
          storage: { type: 'SSD', count: 8, size: 32 },
        },
        {
          name: 'Standard_F8s_v2',
          label: 'Standard_F8s_v2',
          cpu: 8,
          memory: 16,
          storage: { type: 'SSD', count: 16, size: 64 },
        },
        {
          name: 'Standard_F16s_v2',
          label: 'Standard_F16s_v2',
          cpu: 16,
          memory: 32,
          storage: { type: 'SSD', count: 32, size: 128 },
        },
        {
          name: 'Standard_F32s_v2',
          label: 'Standard_F32s_v2',
          cpu: 32,
          memory: 64,
          storage: { type: 'SSD', count: 32, size: 256 },
        },
        {
          name: 'Standard_F64s_v2',
          label: 'Standard_F64s_v2',
          cpu: 64,
          memory: 128,
          storage: { type: 'SSD', count: 32, size: 512 },
        },
        {
          name: 'Standard_F72s_v2',
          label: 'Standard_F72s_v2',
          cpu: 72,
          memory: 144,
          storage: { type: 'SSD', count: 32, size: 576 },
        },
      ],
    };

    const FS = {
      type: 'Fs-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_F1s',
          label: 'Standard_F1s',
          cpu: 1,
          memory: 2,
          storage: { type: 'SSD', count: 4, size: 4 },
        },
        {
          name: 'Standard_F2s',
          label: 'Standard_F2s',
          cpu: 2,
          memory: 4,
          storage: { type: 'SSD', count: 8, size: 8 },
        },
        {
          name: 'Standard_F4s',
          label: 'Standard_F4s',
          cpu: 4,
          memory: 8,
          storage: { type: 'SSD', count: 16, size: 16 },
        },
        {
          name: 'Standard_F8s',
          label: 'Standard_F8s',
          cpu: 8,
          memory: 16,
          storage: { type: 'SSD', count: 32, size: 32 },
        },
        {
          name: 'Standard_F16s',
          label: 'Standard_F16s',
          cpu: 16,
          memory: 32,
          storage: { type: 'SSD', count: 64, size: 64 },
        },
      ],
    };

    const F = {
      type: 'F-series',
      description: '',
      instanceTypes: [
        {
          name: 'Standard_F1',
          label: 'Standard_F1',
          cpu: 1,
          memory: 2,
          storage: { type: 'SSD', count: 4, size: 16 },
        },
        {
          name: 'Standard_F2',
          label: 'Standard_F2',
          cpu: 2,
          memory: 4,
          storage: { type: 'SSD', count: 8, size: 32 },
        },
        {
          name: 'Standard_F4',
          label: 'Standard_F4',
          cpu: 4,
          memory: 8,
          storage: { type: 'SSD', count: 16, size: 64 },
        },
        {
          name: 'Standard_F8',
          label: 'Standard_F8',
          cpu: 8,
          memory: 16,
          storage: { type: 'SSD', count: 32, size: 128 },
        },
        {
          name: 'Standard_F16',
          label: 'Standard_F16',
          cpu: 16,
          memory: 32,
          storage: { type: 'SSD', count: 64, size: 256 },
        },
      ],
    };

    const categories = [
      {
        type: 'general',
        label: 'General Purpose',
        description:
          'Balanced CPU-to-memory ratio. Ideal for testing and development, small to medium databases, and low to medium traffic web servers.',
        families: [B, DSV3, DV3, DSV2, DV2, AV2, DC],
        icon: 'hdd',
      },
      {
        type: 'compute',
        label: 'Compute Optimized',
        description:
          'High CPU-to-memory ratio. Good for medium traffic web servers, network appliances, batch processes, and application servers.',
        families: [FSV2, FS, F],
        icon: 'hdd',
      },
      {
        type: 'custom',
        label: 'Custom Type',
        description: 'Select the instance type below.',
        families: [],
        icon: 'asterisk',
      },
    ];

    function calculateStorage(type) {
      if (!type || !type.storage) {
        return 0;
      }
      return type.storage.count * type.storage.size;
    }

    function buildStats(category) {
      const stats = {
        cpu: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE,
        },
        memory: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE,
        },
        storage: {
          min: Number.MAX_VALUE,
          max: -Number.MAX_VALUE,
        },
        families: [],
      };

      if (category.families && category.families.length) {
        category.families.forEach(function (family) {
          stats.families.push(family.type);
          const cpuMin = _.minBy(family.instanceTypes, 'cpu').cpu || Number.MAX_VALUE;
          const cpuMax = _.maxBy(family.instanceTypes, 'cpu').cpu || -Number.MAX_VALUE;
          const memoryMin = _.minBy(family.instanceTypes, 'memory').memory || Number.MAX_VALUE;
          const memoryMax = _.maxBy(family.instanceTypes, 'memory').memory || -Number.MAX_VALUE;
          const storageMin = calculateStorage(_.minBy(family.instanceTypes, calculateStorage)) || Number.MAX_VALUE;
          const storageMax = calculateStorage(_.maxBy(family.instanceTypes, calculateStorage)) || -Number.MAX_VALUE;

          stats.cpu.min = Math.min(stats.cpu.min, cpuMin);
          stats.cpu.max = Math.max(stats.cpu.max, cpuMax);
          stats.memory.min = Math.min(stats.memory.min, memoryMin);
          stats.memory.max = Math.max(stats.memory.max, memoryMax);
          stats.storage.min = Math.min(stats.storage.min, storageMin);
          stats.storage.max = Math.max(stats.storage.max, storageMax);
        });
      }

      return stats;
    }

    function getCategories() {
      categories.map(function (category) {
        for (const family of category.families) {
          for (const inst of family.instanceTypes) {
            if (inst.costFactor == undefined) inst.costFactor = 0;
          }
        }
        category.stats = buildStats(category);
      });
      return $q.when(categories);
    }

    const getAllTypesByRegion = function getAllTypesByRegion() {
      return getCategories();
    };

    function getAvailableTypesForRegions(locationToInstanceTypesMap, selectedLocations) {
      // This function is only ever called with one location.
      const [location] = selectedLocations;
      return locationToInstanceTypesMap[location];
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
    };
  },
]);
