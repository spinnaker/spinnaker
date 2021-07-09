import { module } from 'angular';
import { PROVIDER_SERVICE_DELEGATE, ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';

export interface IInstanceType {
  account: string;
  key?: string;
  name: string;
  region: string;
}

export interface IInstanceTypesByRegion {
  [region: string]: IInstanceType[];
}

export interface IInstanceStorage {
  type: string;
  size: number;
  count: number;
  isDefault?: boolean;
}

export interface IPreferredInstanceType {
  name: string;
  nameRegex?: RegExp;
  label?: string;
  cpu?: number;
  memory?: number;
  storage?: IInstanceStorage;
  costFactor?: number;
  unavailable?: boolean;
  helpFieldKey?: string;
}

export interface IInstanceTypeFamily {
  type: string;
  description?: string;
  storageHelpFieldKey?: string;
  storageType?: string;
  instanceTypes: IPreferredInstanceType[];
}

export interface IInstanceTypeCategory {
  type: string;
  label?: string;
  families: IInstanceTypeFamily[];
  icon?: string;
}

export interface IInstanceTypeService {
  resolveInstanceTypeDetails?: (instanceType: string) => PromiseLike<IPreferredInstanceType>;
  getCategories(): PromiseLike<IInstanceTypeCategory[]>;
  getAllTypesByRegion(): PromiseLike<IInstanceTypesByRegion>;
  getAvailableTypesForRegions(instanceTypes: string[], regions: string[]): PromiseLike<string[]>;
  getCategoryForInstanceType(instanceType: string): string;
}

export class InstanceTypeService {
  public static $inject = ['providerServiceDelegate'];
  public constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  public getCategories(cloudProvider: string): PromiseLike<IInstanceTypeCategory[]> {
    return this.getDelegate(cloudProvider).getCategories();
  }

  public getAllTypesByRegion(cloudProvider: string): PromiseLike<IInstanceTypesByRegion> {
    return this.getDelegate(cloudProvider).getAllTypesByRegion();
  }

  public getAvailableTypesForRegions(
    cloudProvider: string,
    instanceTypes: string[],
    regions: string[],
  ): PromiseLike<string[]> {
    return this.getDelegate(cloudProvider).getAvailableTypesForRegions(instanceTypes, regions);
  }

  public getCategoryForInstanceType(cloudProvider: string, instanceType: string) {
    return this.getInstanceTypeCategory(cloudProvider, instanceType).then((category: IInstanceTypeCategory) => {
      return category ? category.type : 'custom';
    });
  }

  public getInstanceTypeDetails(cloudProvider: string, instanceType: string): PromiseLike<IPreferredInstanceType> {
    return this.getInstanceTypeCategory(cloudProvider, instanceType).then((category: IInstanceTypeCategory) => {
      if (category && category.families && category.families.length && category.families[0].instanceTypes) {
        return category.families[0].instanceTypes.find((i) => i.name === instanceType);
      } else {
        if (this.getDelegate(cloudProvider).resolveInstanceTypeDetails) {
          return this.getDelegate(cloudProvider).resolveInstanceTypeDetails(instanceType);
        }
        return null;
      }
    });
  }

  private getInstanceTypeCategory(cloudProvider: string, instanceType: string): PromiseLike<IInstanceTypeCategory> {
    return this.getCategories(cloudProvider).then((categories: IInstanceTypeCategory[]) => {
      return (categories || []).find((c) =>
        c.families.some((f) =>
          f.instanceTypes.some((i) => i.name === instanceType || (i.nameRegex && i.nameRegex.test(instanceType))),
        ),
      );
    });
  }

  private getDelegate(cloudProvider: string): IInstanceTypeService {
    return this.providerServiceDelegate.getDelegate<IInstanceTypeService>(
      cloudProvider,
      'instance.instanceTypeService',
    );
  }
}

export const INSTANCE_TYPE_SERVICE = 'spinnaker.core.instanceType.service';
module(INSTANCE_TYPE_SERVICE, [PROVIDER_SERVICE_DELEGATE]).service('instanceTypeService', InstanceTypeService);
