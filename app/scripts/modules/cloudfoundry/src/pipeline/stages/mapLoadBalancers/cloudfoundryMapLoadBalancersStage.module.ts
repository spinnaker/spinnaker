import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryMapLoadBalancersStageConfig } from './CloudfoundryMapLoadBalancersStageConfig';
import { AccountService, ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryMapLoadBalancersExecutionDetails } from './CloudfoundryMapLoadBalancersExecutionDetails';

class CloudFoundryMapLoadBalancersStageCtrl implements IController {
  constructor(public $scope: IScope) {
    'ngInject';
    $scope.accounts = [];
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      $scope.accounts = accounts;
    });
  }
}

export const CLOUD_FOUNDRY_MAP_LOAD_BALANCERS_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.mapLoadBalancersStage';
module(CLOUD_FOUNDRY_MAP_LOAD_BALANCERS_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      cloudProvider: 'cloudfoundry',
      controller: 'cfMapLoadBalancersStageCtrl',
      description: 'Map load balancers',
      executionDetailsSections: [CloudfoundryMapLoadBalancersExecutionDetails, ExecutionDetailsTasks],
      key: 'mapLoadBalancers',
      label: 'Map Load Balancers',
      templateUrl: require('./cloudfoundryMapLoadBalancersStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'target' },
      ],
    });
  })
  .component(
    'cfMapLoadBalancersStage',
    react2angular(CloudfoundryMapLoadBalancersStageConfig, [
      'accounts',
      'application',
      'pipeline',
      'stage',
      'stageFieldUpdated',
    ]),
  )
  .controller('cfMapLoadBalancersStageCtrl', CloudFoundryMapLoadBalancersStageCtrl);
