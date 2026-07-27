import { isFunction } from 'lodash';

import { CloudProviderRegistry } from './CloudProviderRegistry';
import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';
import type { PromiseService } from '../utils/nativePromiseService';

interface DirectServiceConstructor<T> {
  new (promiseService: PromiseService, runtimeServices?: DeckRuntimeServices): T;
  requiresDeckRuntimeServices?: boolean;
}

export class ProviderServiceDelegate {
  private instances = new Map<string, Map<string, unknown>>();
  private runtimeServices: DeckRuntimeServices;

  constructor(private promiseService: PromiseService) {}

  public bindRuntimeServices(runtimeServices: DeckRuntimeServices): void {
    this.runtimeServices = runtimeServices;
  }

  public hasDelegate(provider: string, serviceKey: string): boolean {
    return isFunction(CloudProviderRegistry.getValue(provider, serviceKey));
  }

  public getDelegate<T>(provider: string, serviceKey: string): T {
    const cached = this.instances.get(provider)?.get(serviceKey);
    if (cached) {
      return cached as T;
    }

    const ServiceClass = CloudProviderRegistry.getValue(provider, serviceKey) as DirectServiceConstructor<T>;
    if (isFunction(ServiceClass)) {
      const instance = ServiceClass.requiresDeckRuntimeServices
        ? new ServiceClass(this.promiseService, this.runtimeServices)
        : new ServiceClass(this.promiseService);
      const providerInstances = this.instances.get(provider) || new Map<string, unknown>();
      providerInstances.set(serviceKey, instance);
      this.instances.set(provider, providerInstances);
      return instance;
    }

    throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
  }

  public dispose(): void {
    this.instances.clear();
    this.runtimeServices = null;
  }
}
