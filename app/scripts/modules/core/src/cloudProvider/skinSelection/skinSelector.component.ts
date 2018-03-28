import { IModalInstanceService } from 'angular-ui-bootstrap';

import { IController, module } from 'angular';

import { ACCOUNT_SERVICE } from 'core/account/account.service';
import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';

export class SkinSelectorCtrl implements IController {
  public command =  { skin: '' };

  constructor(public skinOptions: string[],
              private $uibModalInstance: IModalInstanceService) {
    'ngInject';

    if (skinOptions.length > 0) {
      this.command.skin = skinOptions[0];
    }
  }

  public selectVersion(): void {
    this.$uibModalInstance.close(this.command.skin);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const SKIN_SELECTOR_CTRL = 'spinnaker.core.cloudProvider.skinSelector.controller';

module(SKIN_SELECTOR_CTRL, [
  ACCOUNT_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
]).controller('skinSelectorCtrl', SkinSelectorCtrl);
