import { IPromise } from 'angular';
import { API } from '@spinnaker/core';
import { IServiceDiscoveryRegistryDescriptor } from './IServiceDiscovery';

export class ServiceDiscoveryReader {
  public static listServiceDiscoveryRegistries(): IPromise<IServiceDiscoveryRegistryDescriptor[]> {
    return API.all('ecs')
      .all('serviceDiscoveryRegistries')
      .getList();
  }
}
