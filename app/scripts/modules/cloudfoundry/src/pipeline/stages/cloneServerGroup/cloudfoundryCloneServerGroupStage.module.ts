import { IController, IScope, module } from 'angular';
import { react2angular } from 'react2angular';
import { CloudfoundryCloneServerGroupStageConfig } from './CloudfoundryCloneServerGroupStageConfig';
import { IStage, Registry } from '@spinnaker/core';

class CloudfoundryCloneServerGroupStageCtrl implements IController {
  public static $inject = ['$scope'];
  constructor(public $scope: IScope) {}
}

export const CLOUD_FOUNDRY_CLONE_SERVER_GROUP_STAGE = 'spinnaker.cloudfoundry.pipeline.stage.cloneServerGroupStage';
module(CLOUD_FOUNDRY_CLONE_SERVER_GROUP_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      accountExtractor: (stage: IStage) => stage.context.credentials,
      configAccountExtractor: (stage: IStage) => [stage.credentials],
      provides: 'cloneServerGroup',
      key: 'cloneServerGroup',
      cloudProvider: 'cloudfoundry',
      templateUrl: require('./cloudfoundryCloneServerGroupStage.html'),
      controller: 'cfCloneServerGroupStageCtrl',
      validators: [],
    });
  })
  .component(
    'cfCloneServerGroupStage',
    react2angular(CloudfoundryCloneServerGroupStageConfig, ['application', 'pipeline', 'stage', 'stageFieldUpdated']),
  )
  .controller('cfCloneServerGroupStageCtrl', CloudfoundryCloneServerGroupStageCtrl);
