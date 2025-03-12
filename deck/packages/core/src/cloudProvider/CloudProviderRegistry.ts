/* tslint:disable: no-console */
import { cloneDeep, get, isNil, set } from 'lodash';

import { SETTINGS } from '../config/settings';

export interface ICloudProviderLogo {
  path: string;
}

export interface ICloudProviderConfig {
  name: string;
  logo?: ICloudProviderLogo;
  [attribute: string]: any;
}

export class CloudProviderRegistry {
  /*
  Note: Providers don't get $log, so we stick with console statements here
   */
  private static providers = new Map<string, ICloudProviderConfig>();

  public static registerProvider(cloudProvider: string, config: ICloudProviderConfig): void {
    if (SETTINGS.providers[cloudProvider]) {
      this.providers.set(cloudProvider, config);
    }
  }

  public static getProvider(cloudProvider: string): ICloudProviderConfig {
    return this.providers.has(cloudProvider) ? cloneDeep(this.providers.get(cloudProvider)) : null;
  }

  public static listRegisteredProviders(): string[] {
    return Array.from(this.providers.keys());
  }

  public static overrideValue(cloudProvider: string, key: string, overrideValue: any) {
    if (!this.providers.has(cloudProvider)) {
      console.warn(`Cannot override "${key}" for provider "${cloudProvider}" (provider not registered)`);
      return;
    }
    set(this.providers.get(cloudProvider), key, overrideValue);
  }

  public static hasValue(cloudProvider: string, key: string) {
    return this.providers.has(cloudProvider) && this.getValue(cloudProvider, key) !== null;
  }

  public static getValue(cloudProvider: string, key: string): any {
    return get(this.getProvider(cloudProvider), key) ?? null;
  }

  public static isDisabled(cloudProvider: string) {
    // If the adHocInfrastructureWritesEnabled flag when registering provider is not set
    // Infrastructure writes will be enabled (Action buttons will not be disabled)
    if (isNil(CloudProviderRegistry.getValue(cloudProvider, 'adHocInfrastructureWritesEnabled'))) {
      return false;
    }
    return CloudProviderRegistry.getValue(cloudProvider, 'adHocInfrastructureWritesEnabled') === false;
  }
}
