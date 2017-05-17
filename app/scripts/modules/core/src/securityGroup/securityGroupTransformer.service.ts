import {module} from 'angular';

import {ISecurityGroup} from 'core/domain';

export class SecurityGroupTransformerService {
  constructor(private serviceDelegate: any) { 'ngInject'; }

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): ng.IPromise<ISecurityGroup> {
    return this.serviceDelegate.getDelegate(securityGroup.provider || securityGroup.type, 'securityGroup.transformer').normalizeSecurityGroup(securityGroup);
  }
}

export const SECURITY_GROUP_TRANSFORMER_SERVICE = 'spinnaker.core.securityGroup.transformer.service';
module(SECURITY_GROUP_TRANSFORMER_SERVICE, [require('../cloudProvider/serviceDelegate.service.js')])
  .service('securityGroupTransformer', SecurityGroupTransformerService);
