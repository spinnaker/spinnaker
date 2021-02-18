import { IQService, module } from 'angular';

import { ISecurityGroup } from '@spinnaker/core';

class KubernetesV2SecurityGroupTransformer {
  public static $inject = ['$q'];
  constructor(private $q: IQService) {}

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): PromiseLike<ISecurityGroup> {
    return this.$q.resolve(securityGroup);
  }
}

export const KUBERNETES_SECURITY_GROUP_TRANSFORMER = 'spinnaker.kubernetes.securityGroupTransformer';
module(KUBERNETES_SECURITY_GROUP_TRANSFORMER, []).service(
  'kubernetesV2SecurityGroupTransformer',
  KubernetesV2SecurityGroupTransformer,
);
