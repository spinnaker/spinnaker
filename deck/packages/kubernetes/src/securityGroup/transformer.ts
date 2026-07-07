import type { ISecurityGroup } from '@spinnaker/core';

export class KubernetesV2SecurityGroupTransformer {
  public normalizeSecurityGroup(securityGroup: ISecurityGroup): PromiseLike<ISecurityGroup> {
    return Promise.resolve(securityGroup);
  }
}
