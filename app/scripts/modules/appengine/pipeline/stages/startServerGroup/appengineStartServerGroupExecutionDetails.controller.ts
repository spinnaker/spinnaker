import {module} from 'angular';

import {EXECUTION_DETAILS_SECTION_SERVICE,
        ExecutionDetailsSectionService} from 'core/delivery/details/executionDetailsSection.service';
import {BaseExecutionDetailsCtrl,
        IExecutionDetailsScope,
        IExecutionDetailsStateParams} from 'core/pipeline/config/stages/core/baseExecutionDetails.controller';

class AppengineStartServerGroupExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {
  static get $inject() { return ['$scope', '$stateParams', 'executionDetailsSectionService']; }

  constructor (public $scope: IExecutionDetailsScope,
               $stateParams: IExecutionDetailsStateParams,
               executionDetailsSectionService: ExecutionDetailsSectionService) {
    super($scope, $stateParams, executionDetailsSectionService);

    super.setScopeConfigSections(['startServerGroupConfig', 'taskStatus']);
    super.initialize();
  }
}

export const APPENGINE_START_SERVER_GROUP_EXECUTION_DETAILS_CTRL = 'spinnaker.appengine.pipeline.stage.startServerGroup.executionDetails.controller';

module(APPENGINE_START_SERVER_GROUP_EXECUTION_DETAILS_CTRL, [
  require('angular-ui-router'),
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
]).controller('appengineStartServerGroupExecutionDetailsCtrl', AppengineStartServerGroupExecutionDetailsCtrl);
