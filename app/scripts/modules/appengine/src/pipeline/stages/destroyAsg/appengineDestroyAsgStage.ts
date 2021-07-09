import { module } from 'angular';

import { Registry } from '@spinnaker/core';

import { AppengineStageCtrl } from '../appengineStage.controller';
import { IAppengineStageScope } from '../../../domain';

class AppengineDestroyAsgStageCtrl extends AppengineStageCtrl {
  public static $inject = ['$scope'];
  constructor(protected $scope: IAppengineStageScope) {
    super($scope);

    super.setAccounts().then(() => {
      super.setStageRegion();
    });

    super.setStageCloudProvider();
    super.setTargets();
    super.setStageCredentials();
  }
}

export const APPENGINE_DESTROY_ASG_STAGE = 'spinnaker.appengine.pipeline.stage.destroyAsgStage';

module(APPENGINE_DESTROY_ASG_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      provides: 'destroyServerGroup',
      key: 'destroyServerGroup',
      cloudProvider: 'appengine',
      templateUrl: require('./destroyAsgStage.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('appengineDestroyAsgStageCtrl', AppengineDestroyAsgStageCtrl);
