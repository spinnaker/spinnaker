import { module } from 'angular';

const copyStageCardComponent: ng.IComponentOptions = {
  bindings: {
    stageWrapper: '<',
  },
  template: `
    <div class="row">
      <div class="col-md-10">
        <b>{{::$ctrl.stageWrapper.stage.name}}</b>
      </div>
      <div class="col-md-2">
        <cloud-provider-logo ng-if="$ctrl.stageWrapper.stage.cloudProviderType"
                             class="pull-right"
                             height="'10px'"
                             width="'10px'"
                             provider="$ctrl.stageWrapper.stage.cloudProviderType">
        </cloud-provider-logo>
      </div>
    </div>
    <p><b>Type:</b> {{::$ctrl.stageWrapper.stage.type | robotToHuman}}</p>
    <p ng-if="$ctrl.stageWrapper.pipeline"><b>Pipeline:</b> {{::$ctrl.stageWrapper.pipeline}}</p>
    <p ng-if="$ctrl.stageWrapper.strategy"><b>Strategy:</b> {{::$ctrl.stageWrapper.strategy}}</p>
    <p class="small">{{::$ctrl.stageWrapper.stage.comments}}</p>
  `
};

export const COPY_STAGE_CARD_COMPONENT = 'spinnaker.core.copyStageCard.component';

module(COPY_STAGE_CARD_COMPONENT, []).component('copyStageCard', copyStageCardComponent);
