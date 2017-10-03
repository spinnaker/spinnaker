import { IModalInstanceService } from 'angular-ui-bootstrap';

import { IController, module } from 'angular';

import { ACCOUNT_SERVICE } from 'core/account/account.service';
import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';

export class VersionSelectorCtrl implements IController {
  public command: any =  { version: '' };

  constructor(public versionOptions: string[],
              private $uibModalInstance: IModalInstanceService) {
    'ngInject';

    if (versionOptions.length > 0) {
      this.command.version = versionOptions[0];
    }
  }

  public selectVersion(): void {
    this.$uibModalInstance.close(this.command.version);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const VERSION_SELECTOR_CTRL = 'spinnaker.core.cloudProvider.versionSelector.controller';

module(VERSION_SELECTOR_CTRL, [
  ACCOUNT_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
]).controller('versionSelectorCtrl', VersionSelectorCtrl);
