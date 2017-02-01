import {module} from 'angular';
import {IModalServiceInstance} from 'angular-ui-bootstrap';

class EditVipModalCtrl {
  public cancel: (reason?: any) => void;
  public invalid: boolean = false;
  public errorMessage: string = null;

  constructor(public vip: string, private $uibModalInstance: IModalServiceInstance) {
    this.cancel = $uibModalInstance.dismiss;
  }

  public update(): void {
    if (this.vip.length === 0) {
      this.invalid = true;
      this.errorMessage = 'VIP must have a value';
    } else {
      this.$uibModalInstance.close(this.vip);
    }
  }
}

export const EDIT_VIP_MODAL_CONTROLLER = 'spinnaker.netflix.pipeline.stage.isolatedTestingTarget.editVip';

module(EDIT_VIP_MODAL_CONTROLLER, [])
  .controller('EditVipModalCtrl', EditVipModalCtrl);

