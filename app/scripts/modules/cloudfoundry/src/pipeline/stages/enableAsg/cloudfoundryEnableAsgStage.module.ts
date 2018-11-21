import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryEnableAsgStageConfig } from './CloudfoundryEnableAsgStageConfig';
import { Application, IStage, Registry } from '@spinnaker/core';

class CloudFoundryEnableAsgStageCtrl implements IController {
  constructor(public $scope: IScope, private application: Application) {
    'ngInject';
    this.$scope.application = this.application;
  }
}

export const CLOUD_FOUNDRY_ENABLE_ASG_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.enableAsgStage';
module(CLOUD_FOUNDRY_ENABLE_ASG_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'enableServerGroup',
      key: 'enableServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryEnableAsgStage.html'),
      controller: 'cfEnableAsgStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .component(
    'cfEnableAsgStage',
    react2angular(CloudfoundryEnableAsgStageConfig, ['application', 'pipeline', 'stage', 'stageFieldUpdated']),
  )
  .controller('cfEnableAsgStageCtrl', CloudFoundryEnableAsgStageCtrl);
