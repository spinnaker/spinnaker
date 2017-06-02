import {module} from 'angular';
import {StateParams} from '@uirouter/angularjs';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope} from '../../core/baseExecutionDetails.controller';

class DestroyAsgExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  constructor ($scope: IExecutionDetailsScope,
               $stateParams: StateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['destroyServerGroupConfig', 'taskStatus']);
  }
}

export const DESTROY_ASG_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.destroyAsg.executionDetails.controller';

module(DESTROY_ASG_EXECUTION_DETAILS_CTRL, [
  require('@uirouter/angularjs').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
]).controller('destroyAsgExecutionDetailsCtrl', DestroyAsgExecutionDetailsCtrl);
