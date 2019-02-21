import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryDestroyAsgStageConfig } from './CloudfoundryDestroyAsgStageConfig';
import { Application, IStage, Registry } from '@spinnaker/core';

class CloudFoundryDestroyAsgStageCtrl implements IController {
  public static $inject = ['$scope', 'application'];
  constructor(public $scope: IScope, private application: Application) {
    'ngInject';
    this.$scope.application = this.application;
  }
}

export const CLOUD_FOUNDRY_DESTROY_ASG_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.destroyAsgStage';
module(CLOUD_FOUNDRY_DESTROY_ASG_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'destroyServerGroup',
      key: 'destroyServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryDestroyAsgStage.html'),
      controller: 'cfDestroyAsgStageCtrl',
      validators: [
        {
          type: 'cfTargetImpedance',
          message:
            'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .component(
    'cfDestroyAsgStage',
    react2angular(CloudfoundryDestroyAsgStageConfig, ['application', 'pipeline', 'stage', 'stageFieldUpdated']),
  )
  .controller('cfDestroyAsgStageCtrl', CloudFoundryDestroyAsgStageCtrl);
