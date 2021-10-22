import type { IQService } from 'angular';
import { module } from 'angular';
import _ from 'lodash';

import type {
  IInstanceType,
  IInstanceTypeCategory,
  IInstanceTypesByRegion,
  IPreferredInstanceType,
} from '@spinnaker/core';
import { REST } from '@spinnaker/core';

import { categories, defaultCategories } from './awsInstanceTypes';

export interface IAmazonPreferredInstanceType extends IPreferredInstanceType {
  cpuCreditsPerHour?: number;
}

export interface IAmazonInstanceTypeCategory extends IInstanceTypeCategory {
  showCpuCredits?: boolean;
  descriptionListOverride?: string[];
}

export const AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE = 'spinnaker.amazon.instanceType.service';
export const name = AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE; // for backwards compatibility

module(AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE, []).factory('awsInstanceTypeService', [
  '$q',
  function ($q: IQService) {
    function getCategories() {
      return $q.when(categories);
    }

    const getAllTypesByRegion = function getAllTypesByRegion(): PromiseLike<IInstanceTypesByRegion> {
      return REST('/instanceTypes')
        .get()
        .then(function (types) {
          return _.chain(types)
            .map(function (type: IInstanceType) {
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

    function sortTypesByFamilyAndSize(o1: string, o2: string) {
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

    function getAvailableTypesForRegions(availableInstanceTypes: IInstanceTypesByRegion, selectedRegions: string[]) {
      selectedRegions = selectedRegions || [];
      let availableTypes: string[] = [];

      // prime the list of available types
      if (selectedRegions && selectedRegions.length) {
        availableTypes = _.map(availableInstanceTypes[selectedRegions[0]], 'name');
      }

      // this will perform an unnecessary intersection with the first region, which is fine
      selectedRegions.forEach(function (selectedRegion) {
        if (availableInstanceTypes[selectedRegion]) {
          availableTypes = _.intersection(availableTypes, _.map(availableInstanceTypes[selectedRegion], 'name'));
        }
      });

      return availableTypes.sort(sortTypesByFamilyAndSize);
    }

    const families: { [key: string]: string[] } = {
      paravirtual: ['c1', 'c3', 'hi1', 'hs1', 'm1', 'm2', 'm3', 't1'],
      hvm: ['c3', 'c4', 'd2', 'i2', 'g2', 'm3', 'm4', 'm5', 'p2', 'r3', 'r4', 'r5', 't2', 'x1'],
      vpcOnly: ['c4', 'm4', 'm5', 'r4', 'r5', 't2', 'x1'],
      ebsOptimized: ['c4', 'd2', 'f1', 'g3', 'i3', 'm4', 'm5', 'p2', 'r4', 'r5', 'x1'],
      burstablePerf: ['t2', 't3', 't3a', 't4g'],
    };

    function filterInstanceTypes(instanceTypes: string[], virtualizationType: string, vpcOnly: boolean): string[] {
      return instanceTypes.filter((instanceType: string) => {
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

    function isEbsOptimized(instanceType: string) {
      if (!instanceType) {
        return false;
      }
      const family = _.split(instanceType, '.', 1)[0];
      return families.ebsOptimized.includes(family);
    }

    function isBurstingSupportedForAllTypes(instanceTypes: string[]): boolean {
      if (!instanceTypes || !instanceTypes.length) {
        return false;
      }
      // for multiple instance types case, all instance types must support bursting in order to prevent incompatible configuration.
      return instanceTypes.every((instanceType) => isBurstingSupported(instanceType));
    }

    function isBurstingSupported(instanceType: string): boolean {
      if (!instanceType) {
        return false;
      }
      const family = _.split(instanceType, '.', 1)[0];
      return families.burstablePerf.includes(family);
    }

    function getInstanceTypesInCategory(instanceTypesToFilter: string[], category: string): string[] {
      if (!instanceTypesToFilter || !instanceTypesToFilter.length || !category) {
        return [];
      }

      if (category === 'custom') {
        return instanceTypesToFilter;
      }

      const instanceTypesInCategory: string[] = _.flatten(
        _.find(defaultCategories, { type: category })?.families.map((f) => f.instanceTypes.map((it) => it.name)),
      );
      return _.intersection(instanceTypesToFilter, instanceTypesInCategory);
    }

    return {
      getCategories,
      getAvailableTypesForRegions,
      getAllTypesByRegion,
      filterInstanceTypes,
      isEbsOptimized,
      isBurstingSupportedForAllTypes,
      getInstanceTypesInCategory,
    };
  },
]);
