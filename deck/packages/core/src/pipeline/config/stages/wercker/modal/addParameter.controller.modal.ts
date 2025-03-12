import type { IController } from 'angular';
import { module } from 'angular';
import type { IModalInstanceService } from 'angular-ui-bootstrap';

export class WerckerStageAddParameter implements IController {
  public static $inject = ['$scope', '$uibModalInstance'];
  constructor(private $scope: ng.IScope, private $uibModalInstance: IModalInstanceService) {}

  public submit(): void {
    this.$uibModalInstance.close(this.$scope.parameter);
  }
}

export const WERCKER_STAGE_ADD_PARAMETER_MODAL_CONTROLLER = 'spinnaker.core.pipeline.stage.wercker.modal.addParameter';

module(WERCKER_STAGE_ADD_PARAMETER_MODAL_CONTROLLER, []).controller(
  'WerckerStageAddParameterCtrl',
  WerckerStageAddParameter,
);
