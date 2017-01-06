import {module} from 'angular';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope,
        IExecutionDetailsStateParams} from 'core/pipeline/config/stages/core/baseExecutionDetails.controller';

class AppengineStopServerGroupExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  static get $inject() { return ['$scope', '$stateParams', 'executionDetailsSectionService']; }

  constructor (public $scope: IExecutionDetailsScope,
               $stateParams: IExecutionDetailsStateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['stopServerGroupConfig', 'taskStatus']);
    super.initialize();
  }
}

export const APPENGINE_STOP_SERVER_GROUP_EXECUTION_DETAILS_CTRL = 'spinnaker.appengine.pipeline.stage.stopServerGroup.executionDetails.controller';

module(APPENGINE_STOP_SERVER_GROUP_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
]).controller('appengineStopServerGroupExecutionDetailsCtrl', AppengineStopServerGroupExecutionDetailsCtrl);
