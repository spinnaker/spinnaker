/* tslint:disable: no-console */
import { cloneDeep, get, isNil, set, uniq, without } from 'lodash';

import { SETTINGS } from '../config/settings';

export interface ICloudProviderLogo {
  path: string;
}

export interface ICloudProviderConfig {
  name: string;
  logo?: ICloudProviderLogo;
  [attribute: string]: any;
}

class Providers {
  private providers: Array<{ cloudProvider: string; config: ICloudProviderConfig }> = [];

  public set(cloudProvider: string, config: ICloudProviderConfig): void {
    // The original implementation used a Map, so calling #set could overwrite a config.
    // The tests depend on this behavior, but maybe something else does as well.
    this.providers = without(
      this.providers,
      this.providers.find((p) => p.cloudProvider === cloudProvider),
    ).concat([{ cloudProvider, config }]);
  }

  public get(cloudProvider: string): ICloudProviderConfig {
    const provider = this.providers.find((p) => p.cloudProvider === cloudProvider);
    return provider ? provider.config : null;
  }

  public has(cloudProvider: string): boolean {
    return !!this.get(cloudProvider);
  }

  public keys(): string[] {
    return uniq(this.providers.map((p) => p.cloudProvider));
  }
}

export class CloudProviderRegistry {
  /*
  Note: Providers don't get $log, so we stick with console statements here
   */
  private static providers = new Providers();

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

  //If the flag kubernetesAdHocInfraWritesEnabled is set to "false" then is disabled
  public static isDisabled(cloudProvider: string) {
    if (cloudProvider !== 'kubernetes') {
      return false;
    }
    const writesEnabled = CloudProviderRegistry.getValue(cloudProvider, 'kubernetesAdHocInfraWritesEnabled');
    return isNil(writesEnabled) || writesEnabled === false;
  }
}
