import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryDestroyServiceStageConfig } from './CloudfoundryDestroyServiceStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryDestroyServiceExecutionDetails } from './CloudfoundryDestroyServiceExecutionDetails';

class CloudFoundryDestroyServiceStageCtrl implements IController {
  public static $inject = ['$scope'];
  constructor(public $scope: IScope) {}
}

export const CLOUD_FOUNDRY_DESTROY_SERVICE_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.deleteServiceStage';
module(CLOUD_FOUNDRY_DESTROY_SERVICE_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'destroyService',
      key: 'destroyService',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryDestroyServiceStage.html'),
      controller: 'cfDestroyServiceStageCtrl',
      executionDetailsSections: [CloudfoundryDestroyServiceExecutionDetails, ExecutionDetailsTasks],
      validators: [
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .component(
    'cfDestroyServiceStage',
    react2angular(CloudfoundryDestroyServiceStageConfig, ['stage', 'stageFieldUpdated']),
  )
  .controller('cfDestroyServiceStageCtrl', CloudFoundryDestroyServiceStageCtrl);
