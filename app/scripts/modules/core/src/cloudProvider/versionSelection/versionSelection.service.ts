import { IModalService } from 'angular-ui-bootstrap';
import { IPromise, module } from 'angular';
import { isNil, uniq } from 'lodash';

import { ACCOUNT_SERVICE, AccountService } from 'core/account/account.service';
import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry } from 'core/cloudProvider/cloudProvider.registry';

export class VersionSelectionService {
  constructor(private $uibModal: IModalService,
              private accountService: AccountService,
              private cloudProviderRegistry: CloudProviderRegistry) {
    'ngInject';
  }

  public selectVersion(provider: string): IPromise<string> {
    return this.accountService.getAllAccountDetailsForProvider(provider).then(accounts => {
      const versions = uniq(accounts.map((a) => a.providerVersion)
        .filter((v) => !isNil(v)));

      if (versions.length === 0) {
        return this.cloudProviderRegistry.getProvider(provider).providerVersion
      } else if (versions.length === 1) {
        return versions[0];
      } else {
        return this.$uibModal.open({
          templateUrl: require('./versionSelector.html'),
          controller: 'versionSelectorCtrl as ctrl',
          resolve: {
            versionOptions: () => versions
          }
        }).result;
      }
    });
  }
}

export const VERSION_SELECTION_SERVICE = 'spinnaker.core.cloudProvider.versionSelection.service';
module(VERSION_SELECTION_SERVICE, [
  ACCOUNT_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
]).service('versionSelectionService', VersionSelectionService);
