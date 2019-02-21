import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryDisableAsgStageConfig } from './CloudfoundryDisableAsgStageConfig';
import { Application, IStage, Registry } from '@spinnaker/core';

class CloudFoundryDisableAsgStageCtrl implements IController {
  public static $inject = ['$scope', 'application'];
  constructor(public $scope: IScope, private application: Application) {
    this.$scope.application = this.application;
  }
}

export const CLOUD_FOUNDRY_DISABLE_ASG_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.disableAsgStage';
module(CLOUD_FOUNDRY_DISABLE_ASG_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'disableServerGroup',
      key: 'disableServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryDisableAsgStage.html'),
      controller: 'cfDisableAsgStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .component(
    'cfDisableAsgStage',
    react2angular(CloudfoundryDisableAsgStageConfig, ['application', 'pipeline', 'stage', 'stageFieldUpdated']),
  )
  .controller('cfDisableAsgStageCtrl', CloudFoundryDisableAsgStageCtrl);
