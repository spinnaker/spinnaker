import type { IServerGroup } from '@spinnaker/core';

export class ProxmoxServerGroupTransformer {
  public normalizeServerGroup(serverGroup: IServerGroup): PromiseLike<IServerGroup> {
    return Promise.resolve(serverGroup);
  }

  public convertServerGroupCommandToDeployConfiguration(base: any): any {
    return { ...base, cloudProvider: 'proxmox' };
  }
}
