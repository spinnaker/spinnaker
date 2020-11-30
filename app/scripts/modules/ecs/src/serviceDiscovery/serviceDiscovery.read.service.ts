import { REST } from '@spinnaker/core';
import { IServiceDiscoveryRegistryDescriptor } from './IServiceDiscovery';

export class ServiceDiscoveryReader {
  public static listServiceDiscoveryRegistries(): PromiseLike<IServiceDiscoveryRegistryDescriptor[]> {
    return REST('/ecs/serviceDiscoveryRegistries').get();
  }
}
