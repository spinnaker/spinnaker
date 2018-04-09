import { IPromise, module } from 'angular';

import { AccountService, IAccountDetails, ACCOUNT_SERVICE } from 'core/account/account.service';
import { ISecurityGroup } from 'core/domain';
import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

export class SecurityGroupTransformerService {
  constructor(private providerServiceDelegate: ProviderServiceDelegate, private accountService: AccountService) {
    'ngInject';
  }

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): IPromise<ISecurityGroup> {
    return this.accountService.getAccountDetails(securityGroup.account).then((accountDetails: IAccountDetails) => {
      return this.providerServiceDelegate
        .getDelegate<any>(
          securityGroup.provider || securityGroup.type,
          'securityGroup.transformer',
          accountDetails && accountDetails.skin,
        )
        .normalizeSecurityGroup(securityGroup);
    });
  }
}

export const SECURITY_GROUP_TRANSFORMER_SERVICE = 'spinnaker.core.securityGroup.transformer.service';
module(SECURITY_GROUP_TRANSFORMER_SERVICE, [ACCOUNT_SERVICE, PROVIDER_SERVICE_DELEGATE]).service(
  'securityGroupTransformer',
  SecurityGroupTransformerService,
);
