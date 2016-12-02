import {IStageStep} from '../../domain/IStageStep';
import {IExecutionStage} from '../../domain/IExecutionStage';

interface IFailureOnChanges extends ng.IOnChangesObject {
  isFailed: ng.IChangesObject;
  stage: ng.IChangesObject;
}

class StageFailureMessageCtrl implements ng.IComponentController {

  public isFailed: boolean;
  public message: string;
  public messages: string[];
  public stage: IExecutionStage;
  public failedTask: IStageStep;

  public $onInit(): void {
    if (this.stage && this.stage.isFailed || this.stage.isStopped) {
      if (this.isFailed === undefined) {
        this.isFailed = true;
      }
      this.failedTask = this.stage.tasks.find(t => t.status === 'TERMINAL' || t.status === 'STOPPED');
    }
  }

  public $onChanges(changes: IFailureOnChanges): void {
    this.isFailed = changes.isFailed ? changes.isFailed.currentValue : undefined;
    this.$onInit();
  }
}

class StageFailureMessageComponent implements ng.IComponentOptions {
  public bindings: any = {
    isFailed: '<?',
    message: '<',
    messages: '<',
    stage: '<',
  };
  public controller: ng.IComponentController = StageFailureMessageCtrl;
  public template: string = `
    <div class="row" ng-if="$ctrl.isFailed">
      <div class="col-md-12">
        <div class="alert alert-danger">
          <div ng-if="$ctrl.message || !$ctrl.messages.length">
            <h5>Exception <span ng-if="$ctrl.failedTask">( {{$ctrl.failedTask.name | robotToHuman }} )</span></h5>
            <p>
              {{$ctrl.message || 'No reason provided.'}}
            </p>
          </div>
          <div ng-if="$ctrl.messages.length">
            <h5>Exceptions <span ng-if="$ctrl.failedTask">( {{$ctrl.failedTask.name | robotToHuman }} )</span></h5>
            <p ng-repeat="message in $ctrl.messages">
              {{message || 'No reason provided.'}}
            </p>
          </div>
        </div>
      </div>
    </div>
  `;
}


export const STAGE_FAILURE_MESSAGE_COMPONENT = 'spinnaker.core.delivery.stageFailureMessage.component';

angular.module(STAGE_FAILURE_MESSAGE_COMPONENT, [])
  .component('stageFailureMessage', new StageFailureMessageComponent());
