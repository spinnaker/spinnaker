import { ILogService, module } from 'angular';
import { map } from 'lodash';

import { SETTINGS } from 'core/config/settings';
import { AccountService, IAccountDetails } from 'core/account';
import { CloudProviderRegistry } from 'core/cloudProvider';
import { IDeckRootScope } from 'core/domain';

export class VersionedCloudProviderService {

  private accounts: IAccountDetails[] = [];

  constructor(private $rootScope: IDeckRootScope,
              private $log: ILogService,
              private accountService: AccountService,
              private cloudProviderRegistry: CloudProviderRegistry) {
    'ngInject';
  }

  public getValue(cloudProvider: string, accountName: string, key: string): any {
    const account = this.accounts.find(a => a.name === accountName && a.cloudProvider === cloudProvider);
    return (account && account.providerVersion)
      ? this.cloudProviderRegistry.getValue(cloudProvider, key, account.providerVersion)
      : this.cloudProviderRegistry.getValue(cloudProvider, key);
  }

  public initializeAccounts(): void {
    if (!SETTINGS.feature.versionedProviders) {
      return;
    }

    this.$rootScope.accountsLoading = true;
    this.accountService.getCredentialsKeyedByAccount().then(accounts => {
      this.accounts = map(accounts);
    }).catch(() => {
      this.$log.warn('Could not initialize accounts.');
    }).finally(() => {
      this.$rootScope.accountsLoading = false;
    });
  }
}

export const VERSIONED_CLOUD_PROVIDER_SERVICE = 'spinnaker.core.versionedCloudProvider.service';
module(VERSIONED_CLOUD_PROVIDER_SERVICE, [])
  .service('versionedCloudProviderService', VersionedCloudProviderService)
  .run((versionedCloudProviderService: VersionedCloudProviderService) => {
    'ngInject';
    versionedCloudProviderService.initializeAccounts();
  });
