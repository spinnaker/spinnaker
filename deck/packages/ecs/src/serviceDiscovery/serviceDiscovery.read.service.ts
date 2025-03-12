import { REST } from '@spinnaker/core';
import type { IServiceDiscoveryRegistryDescriptor } from './IServiceDiscovery';

export class ServiceDiscoveryReader {
  public static listServiceDiscoveryRegistries(): PromiseLike<IServiceDiscoveryRegistryDescriptor[]> {
    return REST('/ecs/serviceDiscoveryRegistries').get();
  }
}
