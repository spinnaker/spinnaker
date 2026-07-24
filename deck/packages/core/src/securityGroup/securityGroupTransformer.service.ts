import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import type { ISecurityGroup } from '../domain';

export class SecurityGroupTransformerService {
  public static $inject = ['providerServiceDelegate'];
  constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): PromiseLike<ISecurityGroup> {
    return this.providerServiceDelegate
      .getDelegate<any>(securityGroup.provider || securityGroup.type, 'securityGroup.transformer')
      .normalizeSecurityGroup(securityGroup);
  }
}
