import { module } from 'angular';
import { isString, isFunction } from 'lodash';

import IInjectorService = angular.auto.IInjectorService;

import { CloudProviderRegistry } from './CloudProviderRegistry';

export class ProviderServiceDelegate {
  public static $inject = ['$injector'];
  constructor(private $injector: IInjectorService) {
    'ngInject';
  }

  public hasDelegate(provider: string, serviceKey: string, skin?: string): boolean {
    const service: string = CloudProviderRegistry.getValue(provider, serviceKey, skin);
    return this.$injector.has(service);
  }

  public getDelegate<T>(provider: string, serviceKey: string, skin?: string): T {
    const service = CloudProviderRegistry.getValue(provider, serviceKey, skin);
    if (isString(service) && this.$injector.has(service)) {
      // service is a string, it's an AngularJS service
      return this.$injector.get<T>(service, 'providerDelegate');
    } else if (isFunction(service)) {
      // service is a Function, assume it's service class, so new() it
      return new service();
    } else {
      throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
    }
  }
}

export const PROVIDER_SERVICE_DELEGATE = 'spinnaker.core.cloudProvider.service.delegate';
module(PROVIDER_SERVICE_DELEGATE, []).service('providerServiceDelegate', ProviderServiceDelegate);
