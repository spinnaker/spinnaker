import {module} from 'angular';
import {StateParams} from 'angular-ui-router';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope} from '../../core/baseExecutionDetails.controller';

class EnableAsgExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  constructor ($scope: IExecutionDetailsScope,
               $stateParams: StateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['enableServerGroupConfig', 'taskStatus']);
  }
}

export const ENABLE_ASG_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.enableAsg.executionDetails.controller';

module(ENABLE_ASG_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
]).controller('enableAsgExecutionDetailsCtrl', EnableAsgExecutionDetailsCtrl);
