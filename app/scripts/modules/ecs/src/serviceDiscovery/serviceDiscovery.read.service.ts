import { API } from '@spinnaker/core';
import { IServiceDiscoveryRegistryDescriptor } from './IServiceDiscovery';

export class ServiceDiscoveryReader {
  public static listServiceDiscoveryRegistries(): PromiseLike<IServiceDiscoveryRegistryDescriptor[]> {
    return API.path('ecs', 'serviceDiscoveryRegistries').get();
  }
}
