import { REST } from '@spinnaker/core';
import type { IServiceDiscoveryRegistryDescriptor } from './IServiceDiscovery';

export class ServiceDiscoveryReader {
  public static listServiceDiscoveryRegistries(): Promise<IServiceDiscoveryRegistryDescriptor[]> {
    return REST('/ecs/serviceDiscoveryRegistries').get();
  }
}
