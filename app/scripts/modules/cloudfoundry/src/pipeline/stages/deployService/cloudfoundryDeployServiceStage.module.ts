import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryDeployServiceStageConfig } from './CloudfoundryDeployServiceStageConfig';
import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryDeployServiceExecutionDetails } from 'cloudfoundry/pipeline/stages/deployService/CloudfoundryDeployServiceExecutionDetails';

class CloudFoundryDeployServiceStageCtrl implements IController {
  constructor(public $scope: IScope) {
    'ngInject';
  }
}

export const CLOUD_FOUNDRY_DEPLOY_SERVICE_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.deployServiceStage';
module(CLOUD_FOUNDRY_DEPLOY_SERVICE_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'deployService',
      key: 'deployService',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryDeployServiceStage.html'),
      controller: 'cfDeployServiceStageCtrl',
      executionDetailsSections: [CloudfoundryDeployServiceExecutionDetails, ExecutionDetailsTasks],
      validators: [
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'serviceName', preventSave: true },
        { type: 'requiredField', fieldName: 'service', preventSave: true },
        { type: 'requiredField', fieldName: 'servicePlan', preventSave: true },
        { type: 'validServiceParameterJson', fieldName: 'parameters', preventSave: true },
      ],
    });
  })
  .component(
    'cfDeployServiceStage',
    react2angular(CloudfoundryDeployServiceStageConfig, ['stage', 'stageFieldUpdated']),
  )
  .controller('cfDeployServiceStageCtrl', CloudFoundryDeployServiceStageCtrl);
