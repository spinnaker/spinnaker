import { IModalService } from 'angular-ui-bootstrap';
import { IPromise, module } from 'angular';
import { isNil, uniq } from 'lodash';

import { ACCOUNT_SERVICE, AccountService } from 'core/account/account.service';
import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry } from 'core/cloudProvider/cloudProvider.registry';

export class SkinSelectionService {
  constructor(
    private $uibModal: IModalService,
    private accountService: AccountService,
    private cloudProviderRegistry: CloudProviderRegistry,
  ) {
    'ngInject';
  }

  public selectSkin(provider: string): IPromise<string> {
    return this.accountService.getAllAccountDetailsForProvider(provider).then(accounts => {
      const skins = uniq(accounts.map(a => a.skin).filter(v => !isNil(v)));

      if (skins.length === 0) {
        return this.cloudProviderRegistry.getProvider(provider).skin;
      } else if (skins.length === 1) {
        return skins[0];
      } else {
        return this.$uibModal.open({
          templateUrl: require('./skinSelector.html'),
          controller: 'skinSelectorCtrl as ctrl',
          resolve: {
            skinOptions: () => skins,
          },
        }).result;
      }
    });
  }
}

export const SKIN_SELECTION_SERVICE = 'spinnaker.core.cloudProvider.skinSelection.service';
module(SKIN_SELECTION_SERVICE, [ACCOUNT_SERVICE, CLOUD_PROVIDER_REGISTRY]).service(
  'skinSelectionService',
  SkinSelectionService,
);
