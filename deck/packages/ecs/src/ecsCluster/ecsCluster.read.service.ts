import { REST } from '@spinnaker/core';

import type { IEcsCapacityProviderDetails } from './IEcsCapacityProviderDetails';
import type { IEcsClusterDescriptor } from './IEcsCluster';

export class EcsClusterReader {
  public listClusters(): PromiseLike<IEcsClusterDescriptor[]> {
    return REST('/ecs/ecsClusters').get();
  }

  public describeClusters(account: string, region: string): PromiseLike<IEcsCapacityProviderDetails[]> {
    if (account != null && region != null) {
      return REST('/ecs/ecsClusterDescriptions').path(account).path(region).get();
    }
    return {} as PromiseLike<IEcsCapacityProviderDetails[]>;
  }
}
