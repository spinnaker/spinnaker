import { IModalInstanceService } from 'angular-ui-bootstrap';

import { IController, module } from 'angular';

export class SkinSelectorCtrl implements IController {
  public command = { skin: '' };

  public static $inject = ['skinOptions', '$uibModalInstance'];
  constructor(public skinOptions: string[], private $uibModalInstance: IModalInstanceService) {
    'ngInject';

    if (skinOptions.length > 0) {
      this.command.skin = skinOptions[0];
    }
  }

  public selectSkin(): void {
    this.$uibModalInstance.close(this.command.skin);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const SKIN_SELECTOR_CTRL = 'spinnaker.core.cloudProvider.skinSelector.controller';

module(SKIN_SELECTOR_CTRL, []).controller('skinSelectorCtrl', SkinSelectorCtrl);
