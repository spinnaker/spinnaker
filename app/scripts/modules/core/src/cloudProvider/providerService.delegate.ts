import { module } from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import { CloudProviderRegistry } from './CloudProviderRegistry';

export class ProviderServiceDelegate {
  constructor(private $injector: IInjectorService) {
    'ngInject';
  }

  public hasDelegate(provider: string, serviceKey: string, skin?: string): boolean {
    const service: string = CloudProviderRegistry.getValue(provider, serviceKey, skin);
    return this.$injector.has(service);
  }

  public getDelegate<T>(provider: string, serviceKey: string, skin?: string): T {
    const service = CloudProviderRegistry.getValue(provider, serviceKey, skin);
    if (this.$injector.has(service)) {
      return this.$injector.get<T>(service, 'providerDelegate');
    } else {
      throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
    }
  }
}

export const PROVIDER_SERVICE_DELEGATE = 'spinnaker.core.cloudProvider.service.delegate';
module(PROVIDER_SERVICE_DELEGATE, []).service('providerServiceDelegate', ProviderServiceDelegate);
