import {module} from 'angular';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope} from '../../core/baseExecutionDetails.controller';
import {IExecutionDetailsStateParams} from 'core/delivery/delivery.states';

class EnableAsgExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  static get $inject() { return ['$scope', '$stateParams', 'executionDetailsSectionService']; }

  constructor ($scope: IExecutionDetailsScope,
               $stateParams: IExecutionDetailsStateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['enableServerGroupConfig', 'taskStatus']);
    super.initialize();
  }
}

export const ENABLE_ASG_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.enableAsg.executionDetails.controller';

module(ENABLE_ASG_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
]).controller('enableAsgExecutionDetailsCtrl', EnableAsgExecutionDetailsCtrl);
