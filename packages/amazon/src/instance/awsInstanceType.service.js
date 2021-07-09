'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { REST } from '@spinnaker/core';
import { AWSProviderSettings } from '../aws.settings';

export const AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE = 'spinnaker.amazon.instanceType.service';
export const name = AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE; // for backwards compatibility
module(AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE, []).factory('awsInstanceTypeService', [
  '$q',
  function ($q) {
    const m5 = {
      type: 'm5',
      description:
        'm5 instances provide a balance of compute, memory, and network resources. They are a good choice for most applications.',
      instanceTypes: [
        {
          name: 'm5.large',
          label: 'Large',
          cpu: 2,
          memory: 8,
          storage: { type: 'EBS' },
          costFactor: 2,
        },
        {
          name: 'm5.xlarge',
          label: 'XLarge',
          cpu: 4,
          memory: 16,
          storage: { type: 'EBS' },
          costFactor: 3,
        },
        {
          name: 'm5.2xlarge',
          label: '2XLarge',
          cpu: 8,
          memory: 32,
          storage: { type: 'EBS' },
          costFactor: 4,
        },
      ],
    };

    const t2gp = {
      type: 't2',
      description:
        't2 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      instanceTypes: [
        {
          name: 't2.small',
          label: 'Small',
          cpu: 1,
          memory: 2,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't2.medium',
          label: 'Medium',
          cpu: 2,
          memory: 4,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't2.large',
          label: 'Large',
          cpu: 2,
          memory: 8,
          storage: { type: 'EBS' },
          costFactor: 2,
        },
        {
          name: 't2.xlarge',
          label: 'XLarge',
          cpu: 4,
          memory: 16,
          storage: { type: 'EBS' },
          costFactor: 3,
        },
        {
          name: 't2.2xlarge',
          label: '2XLarge',
          cpu: 8,
          memory: 32,
          storage: { type: 'EBS' },
          costFactor: 4,
        },
      ],
    };

    const t3gp = {
      type: 't3',
      description:
        't3 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      instanceTypes: [
        {
          name: 't3.small',
          label: 'Small',
          cpu: 2,
          memory: 2,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't3.medium',
          label: 'Medium',
          cpu: 2,
          memory: 4,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't3.large',
          label: 'Large',
          cpu: 2,
          memory: 8,
          storage: { type: 'EBS' },
          costFactor: 2,
        },
        {
          name: 't3.xlarge',
          label: 'XLarge',
          cpu: 4,
          memory: 16,
          storage: { type: 'EBS' },
          costFactor: 3,
        },
        {
          name: 't3.2xlarge',
          label: '2XLarge',
          cpu: 8,
          memory: 32,
          storage: { type: 'EBS' },
          costFactor: 4,
        },
      ],
    };

    const t2 = {
      type: 't2',
      description:
        't2 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      instanceTypes: [
        {
          name: 't2.nano',
          label: 'Nano',
          cpu: 1,
          memory: 0.5,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't2.micro',
          label: 'Micro',
          cpu: 1,
          memory: 1,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't2.small',
          label: 'Small',
          cpu: 1,
          memory: 2,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
      ],
    };

    const t3 = {
      type: 't3',
      description:
        't3 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
      instanceTypes: [
        {
          name: 't3.nano',
          label: 'Nano',
          cpu: 2,
          memory: 0.5,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't3.micro',
          label: 'Micro',
          cpu: 2,
          memory: 1,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 't3.small',
          label: 'Small',
          cpu: 2,
          memory: 2,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
      ],
    };

    const r5 = {
      type: 'r5',
      description:
        'r5 instances are optimized for memory-intensive applications and have the lowest cost per GiB of RAM among Amazon EC2 instance types.',
      instanceTypes: [
        {
          name: 'r5.large',
          label: 'Large',
          cpu: 2,
          memory: 16,
          storage: { type: 'EBS' },
          costFactor: 1,
        },
        {
          name: 'r5.xlarge',
          label: 'XLarge',
          cpu: 4,
          memory: 32,
          storage: { type: 'EBS' },
          costFactor: 2,
        },
        {
          name: 'r5.2xlarge',
          label: '2XLarge',
          cpu: 8,
          memory: 64,
          storage: { type: 'EBS' },
          costFactor: 2,
        },
        {
          name: 'r5.4xlarge',
          label: '4XLarge',
          cpu: 16,
          memory: 128,
          storage: { type: 'EBS' },
          costFactor: 3,
        },
      ],
    };

    const defaultCategories = [
      {
        type: 'general',
        label: 'General Purpose',
        families: [m5, t2gp, t3gp],
        icon: 'hdd',
      },
      {
        type: 'memory',
        label: 'High Memory',
        families: [r5],
        icon: 'hdd',
      },
      {
        type: 'micro',
        label: 'Micro Utility',
        families: [t2, t3],
        icon: 'hdd',
      },
      {
        type: 'custom',
        label: 'Custom Type',
        families: [],
        icon: 'asterisk',
      },
    ];

    const categories = defaultCategories
      .filter(({ type }) => !_.get(AWSProviderSettings, 'instanceTypes.exclude.categories', []).includes(type))
      .map((category) =>
        Object.assign({}, category, {
          families: category.families.filter(
            ({ type }) => !_.get(AWSProviderSettings, 'instanceTypes.exclude.families', []).includes(type),
          ),
        }),
      );

    function getCategories() {
      return $q.when(categories);
    }

    const getAllTypesByRegion = function getAllTypesByRegion() {
      return REST('/instanceTypes')
        .get()
        .then(function (types) {
          return _.chain(types)
            .map(function (type) {
              return {
                region: type.region,
                account: type.account,
                name: type.name,
                key: [type.region, type.account, type.name].join(':'),
              };
            })
            .uniqBy('key')
            .groupBy('region')
            .value();
        });
    };

    const instanceClassOrder = ['xlarge', 'large', 'medium', 'small', 'micro', 'nano'];

    function sortTypesByFamilyAndSize(o1, o2) {
      const type1 = o1.split('.');
      const type2 = o2.split('.');

      const [family1, class1 = ''] = type1;
      const [family2, class2 = ''] = type2;

      if (family1 !== family2) {
        if (family1 > family2) {
          return 1;
        } else if (family1 < family2) {
          return -1;
        }
        return 0;
      }

      const t1Idx = instanceClassOrder.findIndex((el) => class1.endsWith(el));
      const t2Idx = instanceClassOrder.findIndex((el) => class2.endsWith(el));

      if (t1Idx === -1 || t2Idx === -1) {
        return 0;
      }

      if (t1Idx === 0 && t2Idx === 0) {
        const size1 = parseInt(class1.replace('xlarge', '')) || 0;
        const size2 = parseInt(class2.replace('xlarge', '')) || 0;

        if (size2 < size1) {
          return 1;
        } else if (size2 > size1) {
          return -1;
        }
        return 0;
      }

      if (t1Idx > t2Idx) {
        return -1;
      } else if (t1Idx < t2Idx) {
        return 1;
      }
      return 0;
    }

    function getAvailableTypesForRegions(availableRegions, selectedRegions) {
      selectedRegions = selectedRegions || [];
      let availableTypes = [];

      // prime the list of available types
      if (selectedRegions && selectedRegions.length) {
        availableTypes = _.map(availableRegions[selectedRegions[0]], 'name');
      }

      // this will perform an unnecessary intersection with the first region, which is fine
      selectedRegions.forEach(function (selectedRegion) {
        if (availableRegions[selectedRegion]) {
          availableTypes = _.intersection(availableTypes, _.map(availableRegions[selectedRegion], 'name'));
        }
      });

      return availableTypes.sort(sortTypesByFamilyAndSize);
    }

    const families = {
      paravirtual: ['c1', 'c3', 'hi1', 'hs1', 'm1', 'm2', 'm3', 't1'],
      hvm: ['c3', 'c4', 'd2', 'i2', 'g2', 'm3', 'm4', 'm5', 'p2', 'r3', 'r4', 'r5', 't2', 'x1'],
      vpcOnly: ['c4', 'm4', 'm5', 'r4', 'r5', 't2', 'x1'],
      ebsOptimized: ['c4', 'd2', 'f1', 'g3', 'i3', 'm4', 'm5', 'p2', 'r4', 'r5', 'x1'],
      burstablePerf: ['t2', 't3', 't3a', 't4g'],
    };

    function filterInstanceTypes(instanceTypes, virtualizationType, vpcOnly) {
      return instanceTypes.filter((instanceType) => {
        if (virtualizationType === '*') {
          // show all instance types
          return true;
        }
        const [family] = instanceType.split('.');
        if (!vpcOnly && families.vpcOnly.includes(family)) {
          return false;
        }
        if (!families.paravirtual.includes(family) && virtualizationType === 'hvm') {
          return true;
        }
        return families[virtualizationType].includes(family);
      });
    }

    function isEbsOptimized(instanceType) {
      if (!instanceType) {
        return false;
      }
      const [family] = instanceType.split('.');
      return families.ebsOptimized.includes(family);
    }

    function isBurstingSupported(instanceType) {
      if (!instanceType) {
        return false;
      }
      const [family] = instanceType.split('.');
      return families.burstablePerf.includes(family);
    }

    function isInstanceTypeInCategory(instanceType, category) {
      if (!instanceType || !category) {
        return false;
      }

      if (category === 'custom') {
        return true;
      }

      const [family] = instanceType.split('.');
      const instanceCategory = _.find(defaultCategories, { type: category });
      const familyInCategory = instanceCategory && _.find(instanceCategory.families, { type: family });

      return familyInCategory && familyInCategory.instanceTypes.map((t) => t.name).includes(instanceType);
    }

    return {
      getCategories,
      getAvailableTypesForRegions,
      getAllTypesByRegion,
      filterInstanceTypes,
      isEbsOptimized,
      isBurstingSupported,
      isInstanceTypeInCategory,
    };
  },
]);
