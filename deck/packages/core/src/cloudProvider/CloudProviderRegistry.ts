/* tslint:disable: no-console */
import { cloneDeepWith, get, isFunction, isNil, set } from 'lodash';

import { SETTINGS } from '../config/settings';

export interface ICloudProviderLogo {
  path: string;
}

export interface IApplicationProviderField {
  field: string;
  label: string;
  type: 'boolean';
  helpKey?: string;
}

export interface ICloudProviderConfig {
  name: string;
  logo?: ICloudProviderLogo;
  applicationProviderFields?: IApplicationProviderField[];
  [attribute: string]: any;
}

export class CloudProviderRegistry {
  private static providers = new Map<string, ICloudProviderConfig>();

  public static registerProvider(cloudProvider: string, config: ICloudProviderConfig): void {
    if (SETTINGS.providers[cloudProvider]) {
      this.providers.set(cloudProvider, config);
    }
  }

  public static getProvider(cloudProvider: string): ICloudProviderConfig {
    // Registered config values are frequently React components (function components, or
    // React.forwardRef/memo objects tagged with $$typeof). Deep-cloning those is never what
    // callers want, and under React 17 lodash's cloneDeep silently drops the outer object's
    // own properties (e.g. `displayName`) when it recurses into such an object - pass them
    // through by reference and only clone plain data.
    return this.providers.has(cloudProvider)
      ? cloneDeepWith(this.providers.get(cloudProvider), (value) =>
          isFunction(value) || (value && typeof value === 'object' && '$$typeof' in value) ? value : undefined,
        )
      : null;
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
