import {module} from 'angular';
import {StateParams} from 'angular-ui-router';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope} from '../../core/baseExecutionDetails.controller';

class DisableAsgExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  static get $inject() { return ['$scope', '$stateParams', 'executionDetailsSectionService']; }

  constructor ($scope: IExecutionDetailsScope,
               $stateParams: StateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['disableServerGroupConfig', 'taskStatus']);
    super.initialize();
  }
}

export const DISABLE_ASG_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.disableAsg.executionDetails.controller';

module(DISABLE_ASG_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
]).controller('disableAsgExecutionDetailsCtrl', DisableAsgExecutionDetailsCtrl);
