import {module} from 'angular';
import {IModalServiceInstance} from 'angular-ui-bootstrap';
import IScope = ng.IScope;

require('./cancel.less');

interface ICancelModalScope extends IScope {
  state: any;
  params: any;
  errorMessage: string;
}

export class CancelModalCtrl {

  constructor(public $scope: ICancelModalScope, private $uibModalInstance: IModalServiceInstance, private params: any) {
    this.$scope.params = params;

    this.$scope.state = {
      submitting: false
    };
  }

  public formDisabled = () => this.$scope.state.submitting;

  public showError(exception: string): void {
    this.$scope.state.error = true;
    this.$scope.state.submitting = false;
    this.$scope.errorMessage = exception;
  }

  public confirm(): void {
    if (!this.formDisabled()) {
      this.$scope.state.submitting = true;
      this.params.submitMethod(this.params.reason, this.params.force).then(this.$uibModalInstance.close, this.showError);
    }
  };

  public cancel = () => this.$uibModalInstance.dismiss();
}

export const CANCEL_MODAL_CONTROLLER = 'spinnaker.core.cancelModal.controller';
module(CANCEL_MODAL_CONTROLLER, [
  require('angular-ui-bootstrap')
]).controller('cancelModalCtrl', CancelModalCtrl);
