import { module } from 'angular';

import type { ISecurityGroup } from '@spinnaker/core';

class KubernetesV2SecurityGroupTransformer {
  public normalizeSecurityGroup(securityGroup: ISecurityGroup): PromiseLike<ISecurityGroup> {
    return Promise.resolve(securityGroup);
  }
}

export const KUBERNETES_SECURITY_GROUP_TRANSFORMER = 'spinnaker.kubernetes.securityGroupTransformer';
module(KUBERNETES_SECURITY_GROUP_TRANSFORMER, []).service(
  'kubernetesV2SecurityGroupTransformer',
  KubernetesV2SecurityGroupTransformer,
);
