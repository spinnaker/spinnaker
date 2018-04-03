import { module } from 'angular';

import { ACCOUNT_SERVICE, AccountService, PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from '@spinnaker/core';

import { AppengineHealth } from 'appengine/common/appengineHealth';
import { IAppengineStageScope } from 'appengine/domain';
import { AppengineStageCtrl } from '../appengineStage.controller';

class AppengineEnableAsgStageCtrl extends AppengineStageCtrl {
  constructor(protected $scope: IAppengineStageScope, protected accountService: AccountService) {
    'ngInject';
    super($scope, accountService);

    super.setAccounts()
      .then(() => {
        super.setStageRegion();
      });

    super.setStageCloudProvider();
    super.setTargets();
    super.setStageCredentials();

    if ($scope.stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly) {
      $scope.stage.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
    }
  }
}

export const APPENGINE_ENABLE_ASG_STAGE = 'spinnaker.appengine.pipeline.stage.enableAsgStage';

module(APPENGINE_ENABLE_ASG_STAGE, [
  ACCOUNT_SERVICE,
  PIPELINE_CONFIG_PROVIDER,
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      key: 'enableServerGroup',
      cloudProvider: 'appengine',
      templateUrl: require('./enableAsgStage.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ]
    });
  }).controller('appengineEnableAsgStageCtrl', AppengineEnableAsgStageCtrl);
