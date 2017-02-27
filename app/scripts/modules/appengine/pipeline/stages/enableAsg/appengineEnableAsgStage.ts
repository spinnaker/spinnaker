import {module} from 'angular';

import {IAppengineStageScope} from 'appengine/domain/index';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {AppengineStageCtrl} from '../appengineStage.controller';
import {AppengineHealth} from 'appengine/common/appengineHealth';

class AppengineEnableAsgStageCtrl extends AppengineStageCtrl {
  static get $inject() { return ['$scope', 'accountService']; }

  constructor(protected $scope: IAppengineStageScope, protected accountService: AccountService) {
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
  require('core/application/modal/platformHealthOverride.directive.js'),
  ACCOUNT_SERVICE,
])
  .config(function(pipelineConfigProvider: any) {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'appengine',
      templateUrl: require('./enableAsgStage.html'),
      executionDetailsUrl: require('core/pipeline/config/stages/enableAsg/templates/enableAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ]
    });
  }).controller('appengineEnableAsgStageCtrl', AppengineEnableAsgStageCtrl);
