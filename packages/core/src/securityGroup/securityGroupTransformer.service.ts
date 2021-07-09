import { module } from 'angular';
import { PROVIDER_SERVICE_DELEGATE, ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { ISecurityGroup } from '../domain';

export class SecurityGroupTransformerService {
  public static $inject = ['providerServiceDelegate'];
  constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): PromiseLike<ISecurityGroup> {
    return this.providerServiceDelegate
      .getDelegate<any>(securityGroup.provider || securityGroup.type, 'securityGroup.transformer')
      .normalizeSecurityGroup(securityGroup);
  }
}

export const SECURITY_GROUP_TRANSFORMER_SERVICE = 'spinnaker.core.securityGroup.transformer.service';
module(SECURITY_GROUP_TRANSFORMER_SERVICE, [PROVIDER_SERVICE_DELEGATE]).service(
  'securityGroupTransformer',
  SecurityGroupTransformerService,
);
