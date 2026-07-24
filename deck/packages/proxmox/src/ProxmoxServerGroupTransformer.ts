import type { IServerGroup } from '@spinnaker/core';
import { NameUtils } from '@spinnaker/core';

export class ProxmoxServerGroupTransformer {
  public normalizeServerGroup(serverGroup: IServerGroup): PromiseLike<IServerGroup> {
    // Versioned deployments (cluster-vNNN) get their sequence label from the moniker; groups
    // without a sequence are manually created VMs, so surface that in the pod header via the
    // images slot instead of looking like a normal deployment.
    if (!serverGroup.buildInfo && serverGroup.moniker?.sequence == null) {
      (serverGroup as any).buildInfo = { images: ['unversioned (manually created)'] };
    }
    return Promise.resolve(serverGroup);
  }

  public convertServerGroupCommandToDeployConfiguration(base: any): any {
    // Strip UI-only state and fill in the fields clouddriver's ProxmoxDeployDescription
    // derives from the moniker (VM name + Spinnaker tags).
    const { backingData, viewState, selectedProvider, ...command } = base;
    const clusterName = NameUtils.getClusterName(command.application, command.stack, command.freeFormDetails);
    return {
      ...command,
      cloudProvider: 'proxmox',
      account: command.account ?? command.credentials,
      name: command.name ?? clusterName,
      moniker: command.moniker ?? {
        app: command.application,
        cluster: clusterName,
        stack: command.stack || undefined,
        detail: command.freeFormDetails || undefined,
      },
    };
  }
}
