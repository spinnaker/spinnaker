import type { IController } from 'angular';
import { module } from 'angular';
import type { IModalInstanceService } from 'angular-ui-bootstrap';

export class WebhookStageAddCustomHeader implements IController {
  public static $inject = ['$scope', '$uibModalInstance'];
  constructor(private $scope: ng.IScope, private $uibModalInstance: IModalInstanceService) {}

  public submit(): void {
    this.$uibModalInstance.close(this.$scope.customHeader);
  }
}

export const WEBHOOK_STAGE_ADD_CUSTOM_HEADER_MODAL_CONTROLLER =
  'spinnaker.core.pipeline.stage.webhookStage.modal.addCustomHeader';

module(WEBHOOK_STAGE_ADD_CUSTOM_HEADER_MODAL_CONTROLLER, []).controller(
  'WebhookStageAddCustomHeaderCtrl',
  WebhookStageAddCustomHeader,
);
