import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudfoundryRollbackClusterStageConfig } from './CloudfoundryRollbackClusterStageConfig';
import { Application, IStage, Registry } from '@spinnaker/core';

class CloudFoundryRollbackClusterStageCtrl implements IController {
  constructor(public $scope: IScope, private application: Application) {
    'ngInject';
    this.$scope.application = this.application;
  }
}

export const CLOUD_FOUNDRY_ROLLBACK_CLUSTER_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.rollbackClusterStage';
module(CLOUD_FOUNDRY_ROLLBACK_CLUSTER_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'rollbackCluster',
      key: 'rollbackCluster',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryRollbackClusterStage.html'),
      controller: 'cfRollbackClusterStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .component(
    'cfRollbackClusterStage',
    react2angular(CloudfoundryRollbackClusterStageConfig, ['application', 'pipeline', 'stage', 'stageFieldUpdated']),
  )
  .controller('cfRollbackClusterStageCtrl', CloudFoundryRollbackClusterStageCtrl);
