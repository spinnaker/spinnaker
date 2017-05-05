import {module} from 'angular';

import {IAppengineStageScope} from 'appengine/domain/index';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {AppengineStageCtrl} from '../appengineStage.controller';
import {AppengineHealth} from 'appengine/common/appengineHealth';

class AppengineDisableAsgStageCtrl extends AppengineStageCtrl {
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

export const APPENGINE_DISABLE_ASG_STAGE = 'spinnaker.appengine.pipeline.stage.disableAsgStage';

module(APPENGINE_DISABLE_ASG_STAGE, [
  require('core/application/modal/platformHealthOverride.directive.js'),
  ACCOUNT_SERVICE,
])
  .config(function(pipelineConfigProvider: any) {
    pipelineConfigProvider.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'appengine',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: require('core/pipeline/config/stages/disableAsg/templates/disableAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('appengineDisableAsgStageCtrl', AppengineDisableAsgStageCtrl);
