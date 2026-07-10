import type { IServerGroup } from '@spinnaker/core';

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
    return { ...base, cloudProvider: 'proxmox' };
  }
}
