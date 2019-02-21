import { module, IQService, IPromise } from 'angular';

import { ISecurityGroup } from '@spinnaker/core';

class KubernetesV2SecurityGroupTransformer {
  public static $inject = ['$q'];
  constructor(private $q: IQService) {
    'ngInject';
  }

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): IPromise<ISecurityGroup> {
    return this.$q.resolve(securityGroup);
  }
}

export const KUBERNETES_V2_SECURITY_GROUP_TRANSFORMER = 'spinnaker.kubernetes.v2.securityGroupTransformer';
module(KUBERNETES_V2_SECURITY_GROUP_TRANSFORMER, []).service(
  'kubernetesV2SecurityGroupTransformer',
  KubernetesV2SecurityGroupTransformer,
);
