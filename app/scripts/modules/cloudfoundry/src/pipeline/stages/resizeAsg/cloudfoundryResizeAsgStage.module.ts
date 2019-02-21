import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryResizeAsgStageConfig } from './CloudfoundryResizeAsgStageConfig';
import { Application, IStage, Registry } from '@spinnaker/core';
import { IInstanceFieldSizeValidationConfig } from 'cloudfoundry/pipeline/config/validation/instanceSize.validator';

class CloudFoundryResizeAsgStageCtrl implements IController {
  public static $inject = ['$scope', 'application'];
  constructor(public $scope: IScope, private application: Application) {
    'ngInject';
    this.$scope.application = this.application;
  }
}

export const CLOUD_FOUNDRY_RESIZE_ASG_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.resizeAsgStage';
module(CLOUD_FOUNDRY_RESIZE_ASG_STAGE, [])
  .config(function() {
    const instanceCountValidator: IInstanceFieldSizeValidationConfig = {
      type: 'cfInstanceSizeField',
      fieldName: 'capacity.desired',
      fieldLabel: 'Instances',
      min: 0,
      preventSave: true,
    };
    const memoryValidator: IInstanceFieldSizeValidationConfig = {
      type: 'cfInstanceSizeField',
      fieldName: 'memory',
      fieldLabel: 'Mem Mb',
      min: 256,
      preventSave: true,
    };
    const diskValidator: IInstanceFieldSizeValidationConfig = {
      type: 'cfInstanceSizeField',
      fieldName: 'diskQuota',
      fieldLabel: 'Disk Mb',
      min: 256,
      preventSave: true,
    };
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'resizeServerGroup',
      key: 'resizeServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryResizeAsgStage.html'),
      controller: 'cfResizeAsgStageCtrl',
      validators: [
        {
          type: 'cfTargetImpedance',
          message:
            'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
        instanceCountValidator,
        memoryValidator,
        diskValidator,
      ],
    });
  })
  .component(
    'cfResizeAsgStage',
    react2angular(CloudfoundryResizeAsgStageConfig, ['application', 'pipeline', 'stage', 'stageFieldUpdated']),
  )
  .controller('cfResizeAsgStageCtrl', CloudFoundryResizeAsgStageCtrl);
