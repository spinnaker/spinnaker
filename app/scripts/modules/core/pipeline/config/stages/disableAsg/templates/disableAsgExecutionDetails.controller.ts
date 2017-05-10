import {module} from 'angular';
import {StateParams} from 'angular-ui-router';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope} from '../../core/baseExecutionDetails.controller';

class DisableAsgExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  constructor ($scope: IExecutionDetailsScope,
               $stateParams: StateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['disableServerGroupConfig', 'taskStatus']);
  }
}

export const DISABLE_ASG_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.disableAsg.executionDetails.controller';

module(DISABLE_ASG_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
]).controller('disableAsgExecutionDetailsCtrl', DisableAsgExecutionDetailsCtrl);
