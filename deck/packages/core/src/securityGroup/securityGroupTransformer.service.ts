import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import type { ISecurityGroup } from '../domain';

export class SecurityGroupTransformerService {
  constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): Promise<ISecurityGroup> {
    return Promise.resolve(
      this.providerServiceDelegate
        .getDelegate<any>(securityGroup.provider || securityGroup.type, 'securityGroup.transformer')
        .normalizeSecurityGroup(securityGroup),
    );
  }
}
