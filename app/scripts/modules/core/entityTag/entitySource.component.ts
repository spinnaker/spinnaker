import {module} from 'angular';
import {ICreationMetadataTag} from 'core/domain/IEntityTags';
import {IExecution} from '../domain/IExecution';

class EntitySourceCtrl implements ng.IComponentController {
  public relativePath = '^.^.^';
  public metadata: ICreationMetadataTag;
  public executionType: string;
  public popoverTemplate: string = require('./entitySource.popover.html');
  public execution: IExecution;
  public executionNotFound: boolean;

  static get $inject() { return ['executionService']; }

  constructor(private executionService: any) {}

  public $onInit(): void {
    this.executionType = 'Task';
    if (this.metadata && this.metadata.value.executionType === 'pipeline') {
      this.executionType = 'Pipeline';
      this.executionService.getExecution(this.metadata.value.executionId).then(
        (execution: IExecution) => this.execution = execution,
        () => this.executionNotFound = true
      );
    }
  }

  public $onChanges(): void {
    this.$onInit();
  }

}

class EntitySourceComponent implements ng.IComponentOptions {
  public bindings: any = {
    metadata: '=',
    relativePath: '=?',
  };
  public controller: any = EntitySourceCtrl;
  public template = `
    <div ng-if="$ctrl.metadata">
      <dt>Source</dt>
      <dd>
        <span uib-popover-template="$ctrl.popoverTemplate"
              popover-placement="left"
              popover-trigger="mouseenter">
          <span ng-if="$ctrl.executionNotFound">pipeline (not found)</span>
          <a ng-if="!$ctrl.executionNotFound && $ctrl.metadata.value.executionType === 'pipeline'" 
             ui-sref="{{$ctrl.relativePath}}.pipelines.executionDetails.execution({application: $ctrl.metadata.value.application, executionId: $ctrl.metadata.value.executionId, stageId: $ctrl.metadata.value.stageId})">
            pipeline
          </a>
          <a ng-if="$ctrl.metadata.value.executionType === 'orchestration'"
             ui-sref="{{$ctrl.relativePath}}.tasks.taskDetails({application: $ctrl.metadata.value.application, taskId: $ctrl.metadata.value.executionId})">
            task
          </a>
        </span>
      </dd>
    </div>
  `;
}

export const ENTITY_SOURCE_COMPONENT = 'spinnaker.core.entityTag.entitySource.component';
module(ENTITY_SOURCE_COMPONENT, []).component('entitySource', new EntitySourceComponent());
