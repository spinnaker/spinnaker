import {module} from 'angular';

import {IAppengineStageScope} from 'appengine/domain/index';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {AppengineStageCtrl} from '../appengineStage.controller';

class AppengineDestroyAsgStageCtrl extends AppengineStageCtrl {
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
  }
}

export const APPENGINE_DESTROY_ASG_STAGE = 'spinnaker.appengine.pipeline.stage.destroyAsgStage';

module(APPENGINE_DESTROY_ASG_STAGE, [ACCOUNT_SERVICE])
  .config(function(pipelineConfigProvider: any) {
    pipelineConfigProvider.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'appengine',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: require('core/pipeline/config/stages/destroyAsg/templates/destroyAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('appengineDestroyAsgStageCtrl', AppengineDestroyAsgStageCtrl);
