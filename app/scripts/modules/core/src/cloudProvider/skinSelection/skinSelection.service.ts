import { IModalService } from 'angular-ui-bootstrap';
import { IPromise, module } from 'angular';
import { isNil, uniq } from 'lodash';

import { AccountService } from 'core/account/AccountService';
import { CloudProviderRegistry } from 'core/cloudProvider';

export class SkinSelectionService {
  public static $inject = ['$uibModal'];
  constructor(private $uibModal: IModalService) {}

  public selectSkin(provider: string): IPromise<string> {
    return AccountService.getAllAccountDetailsForProvider(provider).then(accounts => {
      const skins = uniq(accounts.map(a => a.skin).filter(v => !isNil(v)));

      if (skins.length === 0) {
        return CloudProviderRegistry.getProvider(provider).skin;
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
module(SKIN_SELECTION_SERVICE, []).service('skinSelectionService', SkinSelectionService);
