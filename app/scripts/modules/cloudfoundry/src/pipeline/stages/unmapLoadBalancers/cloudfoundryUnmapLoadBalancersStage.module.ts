import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryUnmapLoadBalancersStageConfig } from './CloudfoundryUnmapLoadBalancersStageConfig';
import { AccountService, ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { CloudfoundryUnmapLoadBalancersExecutionDetails } from './CloudfoundryUnmapLoadBalancersExecutionDetails';

class CloudFoundryUnmapLoadBalancersStageCtrl implements IController {
  public static $inject = ['$scope'];
  constructor(public $scope: IScope) {
    'ngInject';
    $scope.accounts = [];
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      $scope.accounts = accounts;
    });
  }
}

export const CLOUD_FOUNDRY_UNMAP_LOAD_BALANCERS_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.unmapLoadBalancersStage';
module(CLOUD_FOUNDRY_UNMAP_LOAD_BALANCERS_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      cloudProvider: 'cloudfoundry',
      controller: 'cfUnmapLoadBalancersStageCtrl',
      description: 'Unmap load balancers',
      executionDetailsSections: [CloudfoundryUnmapLoadBalancersExecutionDetails, ExecutionDetailsTasks],
      key: 'unmapLoadBalancers',
      label: 'Unmap Load Balancers',
      templateUrl: require('./cloudfoundryUnmapLoadBalancersStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'target' },
      ],
    });
  })
  .component(
    'cfUnmapLoadBalancersStage',
    react2angular(CloudfoundryUnmapLoadBalancersStageConfig, [
      'accounts',
      'application',
      'pipeline',
      'stage',
      'stageFieldUpdated',
    ]),
  )
  .controller('cfUnmapLoadBalancersStageCtrl', CloudFoundryUnmapLoadBalancersStageCtrl);
