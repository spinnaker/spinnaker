import type { IQService } from 'angular';
import { module } from 'angular';
import { isFunction, isString } from 'lodash';

import { CloudProviderRegistry } from './CloudProviderRegistry';
import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';

import IInjectorService = angular.auto.IInjectorService;

interface DirectServiceConstructor<T> {
  new (promiseService: IQService, runtimeServices?: DeckRuntimeServices): T;
  requiresDeckRuntimeServices?: boolean;
}

export class DirectProviderServiceDelegate {
  private instances = new Map<string, Map<string, unknown>>();
  private runtimeServices: DeckRuntimeServices;

  constructor(private promiseService: IQService) {}

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

export class ProviderServiceDelegate {
  public static $inject = ['$injector', '$q'];
  constructor(private $injector: IInjectorService, private $q: IQService) {}

  public hasDelegate(provider: string, serviceKey: string): boolean {
    const service: string = CloudProviderRegistry.getValue(provider, serviceKey);
    return isFunction(service) || (isString(service) && this.$injector.has(service));
  }

  public getDelegate<T>(provider: string, serviceKey: string): T {
    const service = CloudProviderRegistry.getValue(provider, serviceKey);
    if (isString(service) && this.$injector.has(service)) {
      // service is a string, it's an AngularJS service
      return this.$injector.get<T>(service, 'providerDelegate');
    } else if (isFunction(service)) {
      // service is a Function, assume it's service class, so new() it
      // Inject $q in case it is required for resolving promises in a possibly non-Angular component
      return new service(this.$q);
    } else {
      throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
    }
  }
}

export const PROVIDER_SERVICE_DELEGATE = 'spinnaker.core.cloudProvider.service.delegate';
module(PROVIDER_SERVICE_DELEGATE, []).service('providerServiceDelegate', ProviderServiceDelegate);
