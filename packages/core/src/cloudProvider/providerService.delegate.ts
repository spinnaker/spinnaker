import { IQService, module } from 'angular';
import { isFunction, isString } from 'lodash';

import { CloudProviderRegistry } from './CloudProviderRegistry';

import IInjectorService = angular.auto.IInjectorService;

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
