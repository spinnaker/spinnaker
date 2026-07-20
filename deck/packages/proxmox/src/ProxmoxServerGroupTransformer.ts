import type { IServerGroup } from '@spinnaker/core';
import { NameUtils } from '@spinnaker/core';

export class ProxmoxServerGroupTransformer {
  public normalizeServerGroup(serverGroup: IServerGroup): PromiseLike<IServerGroup> {
    // Show the server group name in the cluster pod header via the images slot,
    // since Proxmox doesn't use Frigga naming (moniker.sequence is always null).
    if (!serverGroup.buildInfo) {
      (serverGroup as any).buildInfo = { images: [serverGroup.name] };
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
