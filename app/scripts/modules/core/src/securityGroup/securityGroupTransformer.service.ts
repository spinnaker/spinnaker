import { IPromise, module } from 'angular';

import { ISecurityGroup } from 'core/domain';
import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

export class SecurityGroupTransformerService {
  constructor(private providerServiceDelegate: ProviderServiceDelegate) { 'ngInject'; }

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): IPromise<ISecurityGroup> {
    return this.providerServiceDelegate
      .getDelegate<any>(securityGroup.provider || securityGroup.type, 'securityGroup.transformer')
      .normalizeSecurityGroup(securityGroup);
  }
}

export const SECURITY_GROUP_TRANSFORMER_SERVICE = 'spinnaker.core.securityGroup.transformer.service';
module(SECURITY_GROUP_TRANSFORMER_SERVICE, [ PROVIDER_SERVICE_DELEGATE ])
  .service('securityGroupTransformer', SecurityGroupTransformerService);
