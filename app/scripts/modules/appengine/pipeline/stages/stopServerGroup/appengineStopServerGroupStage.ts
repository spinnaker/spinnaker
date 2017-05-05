import {module} from 'angular';

import {IAppengineStageScope} from 'appengine/domain/index';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {AppengineStageCtrl} from '../appengineStage.controller';
import {AppengineHealth} from 'appengine/common/appengineHealth';

class AppengineStopServerGroupStageCtrl extends AppengineStageCtrl {
  constructor(public $scope: IAppengineStageScope, protected accountService: AccountService) {
    'ngInject';
    super($scope, accountService);

    super.setAccounts().then(() => { super.setStageRegion(); });

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

export const APPENGINE_STOP_SERVER_GROUP_STAGE = 'spinnaker.appengine.pipeline.stage.stopServerGroupStage';

module(APPENGINE_STOP_SERVER_GROUP_STAGE, [ACCOUNT_SERVICE])
  .config(function(pipelineConfigProvider: any) {
    pipelineConfigProvider.registerStage({
      label: 'Stop Server Group',
      description: 'Stops a server group.',
      key: 'stopAppEngineServerGroup',
      templateUrl: require('./stopServerGroupStage.html'),
      executionDetailsUrl: require('./stopServerGroupExecutionDetails.html'),
      executionStepLabelUrl: require('./stopServerGroupStepLabel.html'),
      controller: 'appengineStopServerGroupStageCtrl',
      controllerAs: 'stopServerGroupStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
      cloudProvider: 'appengine',
    });
  })
  .controller('appengineStopServerGroupStageCtrl', AppengineStopServerGroupStageCtrl);
