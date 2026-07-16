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

export interface IAmazonInstanceType extends IInstanceType {
  defaultVCpus: number;
  memoryInGiB: number;
  hypervisor: string;
  instanceStorageInfo?: IAmazonInstanceTypeStorageInfo;
  ebsInfo?: IAmazonInstanceTypeEbsInfo;
  gpuInfo?: IAmazonInstanceGpuInfo;

  instanceStorageSupported: boolean;
  currentGeneration: boolean;
  bareMetal: boolean;
  ipv6Supported?: boolean;
  burstablePerformanceSupported: boolean;

  supportedArchitectures: string[];
  supportedUsageClasses: string[];
  supportedRootDeviceTypes: string[];
  supportedVirtualizationTypes: string[];
}

export interface IAmazonInstanceTypeStorageInfo {
  storageTypes: string;
  totalSizeInGB?: number;
  nvmeSupport?: string;
}

export interface IAmazonInstanceTypeEbsInfo {
  ebsOptimizedSupport: string;
  nvmeSupport?: string;
  encryptionSupport: string;
}

export interface IAmazonInstanceGpuInfo {
  totalGpuMemoryInMiB: number;
  gpus: IAmazonInstanceGpuDeviceInfo[];
}

export interface IAmazonInstanceGpuDeviceInfo {
  name: string;
  manufacturer: string;
  count: number;
  gpuSizeInMiB: number;
}

export interface IAmazonInstanceTypesByRegion extends IInstanceTypesByRegion {
  [region: string]: IAmazonInstanceType[];
}

export class AwsInstanceTypeService {
  private instanceClassOrder = ['xlarge', 'large', 'medium', 'small', 'micro', 'nano'];
  private families: { [key: string]: string[] } = {
    ec2ClassicSupported: ['m1', 'm3', 't1', 'c1', 'c3', 'cc2', 'cr1', 'm2', 'r3', 'd2', 'hs1', 'i2', 'g2'],
    ebsOptimized: ['c4', 'd2', 'f1', 'g3', 'i3', 'm4', 'm5', 'p2', 'r4', 'r5', 'x1'],
    burstablePerf: ['t2', 't3', 't3a', 't4g'],
  };

  public getCategories(): PromiseLike<IAmazonInstanceTypeCategory[]> {
    return Promise.resolve(categories);
  }

  public getAllTypesByRegion(): PromiseLike<IAmazonInstanceTypesByRegion> {
    return REST('/instanceTypes')
      .get()
      .then(function (types) {
        return _.chain(types)
          .map(function (type: IAmazonInstanceType) {
            return {
              ...type,
              key: [type.region, type.account, type.name].join(':'),
            };
          })
          .uniqBy('key')
          .groupBy('region')
          .value();
      });
  }

  private sortTypesByFamilyAndSize(o1: string, o2: string) {
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

    const t1Idx = this.instanceClassOrder.findIndex((el) => class1.endsWith(el));
    const t2Idx = this.instanceClassOrder.findIndex((el) => class2.endsWith(el));

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

  public getAvailableTypesForRegions(
    availableInstanceTypes: IAmazonInstanceTypesByRegion,
    selectedRegions: string[],
  ): IAmazonInstanceType[] {
    selectedRegions = selectedRegions || [];
    let availableTypes: IAmazonInstanceType[] = [];

    // prime the list of available types
    if (selectedRegions && selectedRegions.length) {
      availableTypes = availableInstanceTypes[selectedRegions[0]] || [];
    }

    // this will perform an unnecessary intersection with the first region, which is fine
    selectedRegions.forEach(function (selectedRegion) {
      if (availableInstanceTypes[selectedRegion]) {
        availableTypes = _.intersectionBy(availableTypes, availableInstanceTypes[selectedRegion], 'name');
      }
    });

    return availableTypes?.sort((a, b) => this.sortTypesByFamilyAndSize(a.name, b.name));
  }

  public filterInstanceTypes(
    instanceTypes: IAmazonInstanceType[],
    virtualizationType: string,
    vpcConfigured: boolean,
    architecture: string,
  ): IAmazonInstanceType[] {
    return _.filter(instanceTypes, (i) => {
      if (virtualizationType === '*' && architecture === '*') {
        // show all instance types
        return true;
      }

      if (!vpcConfigured && !this.families.ec2ClassicSupported.includes(i.name.split('.')[0])) {
        return false;
      }

      if (
        virtualizationType &&
        i.supportedVirtualizationTypes &&
        !i.supportedVirtualizationTypes.includes(virtualizationType)
      ) {
        return false;
      }

      if (architecture && i.supportedArchitectures && !i.supportedArchitectures.includes(architecture)) {
        return false;
      }

      return true;
    });
  }

  public isEbsOptimized(instanceType: string) {
    if (!instanceType) {
      return false;
    }
    const family = _.split(instanceType, '.', 1)[0];
    return this.families.ebsOptimized.includes(family);
  }

  public isBurstingSupportedForAllTypes(instanceTypes: string[]): boolean {
    if (!instanceTypes || !instanceTypes.length) {
      return false;
    }
    // for multiple instance types case, all instance types must support bursting in order to prevent incompatible configuration.
    return instanceTypes.every((instanceType) => this.isBurstingSupported(instanceType));
  }

  public isBurstingSupported(instanceType: string): boolean {
    if (!instanceType) {
      return false;
    }
    const family = _.split(instanceType, '.', 1)[0];
    return this.families.burstablePerf.includes(family);
  }

  public getInstanceTypesInCategory(instanceTypesToFilter: string[], category: string): string[] {
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
}
