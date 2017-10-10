import { module } from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import { CLOUD_PROVIDER_REGISTRY } from './cloudProvider.registry';

export class ProviderServiceDelegate {

  constructor(private $injector: IInjectorService, private cloudProviderRegistry: any) { 'ngInject'; }

  public hasDelegate(provider: string, serviceKey: string, providerVersion?: string): boolean {
    const service: string = this.cloudProviderRegistry.getValue(provider, serviceKey, providerVersion);
    return this.$injector.has(service);
  }

  public getDelegate<T>(provider: string, serviceKey: string, providerVersion?: string): T {
    const service = this.cloudProviderRegistry.getValue(provider, serviceKey, providerVersion);
    if (this.$injector.has(service)) {
      return this.$injector.get<T>(service, 'providerDelegate');
    } else {
      throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
    }
  }
}

export const PROVIDER_SERVICE_DELEGATE = 'spinnaker.core.cloudProvider.service.delegate';
module(PROVIDER_SERVICE_DELEGATE, [ CLOUD_PROVIDER_REGISTRY ])
  .service('providerServiceDelegate', ProviderServiceDelegate);
