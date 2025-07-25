import type { Application } from '@spinnaker/core';
import type { IKubernetesServerGroup, IKubernetesServerGroupManager } from '../interfaces';

export class KubernetesV2ServerGroupTransformer {
  public normalizeServerGroup(
    serverGroup: IKubernetesServerGroup,
    application: Application,
  ): PromiseLike<IKubernetesServerGroup> {
    return application
      .getDataSource('serverGroupManagers')
      .ready()
      .then((sgManagers: IKubernetesServerGroupManager[]) => {
        (serverGroup.serverGroupManagers || []).forEach((managerRef) => {
          const sgManager = sgManagers.find(
            (manager: IKubernetesServerGroupManager) =>
              managerRef.account == manager.account &&
              managerRef.location == manager.region &&
              `${manager.kind} ${managerRef.name}` == manager.name,
          );
          if (sgManager) {
            managerRef.name = sgManager.name;
          }
        });
        return serverGroup;
      });
  }
}
