import {module} from 'angular';
import {StateParams} from 'angular-ui-router';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope} from '../../core/baseExecutionDetails.controller';

class ShrinkClusterExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  constructor (public $scope: IExecutionDetailsScope,
               $stateParams: StateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    'ngInject';
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['shrinkClusterConfig', 'taskStatus']);
  }
}

export const SHRINK_CLUSTER_EXECUTION_DETAILS_CTRL = 'spinnaker.core.pipeline.stage.shrinkCluster.executionDetails.controller';

module(SHRINK_CLUSTER_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router').default,
  EXECUTION_DETAILS_SECTION_SERVICE,
]).controller('shrinkClusterExecutionDetailsCtrl', ShrinkClusterExecutionDetailsCtrl);
