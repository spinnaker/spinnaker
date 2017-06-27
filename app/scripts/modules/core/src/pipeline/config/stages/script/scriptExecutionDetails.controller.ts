import { module } from 'angular';
import { get } from 'lodash';
import { StateParams } from '@uirouter/core';

import { BaseExecutionDetailsCtrl, IExecutionDetailsScope } from '../core/baseExecutionDetails.controller';
import { ExecutionDetailsSectionService, IStage } from '@spinnaker/core';

export class ScriptExecutionDetailsCtrl extends BaseExecutionDetailsCtrl {

  public scriptRanAndFailed = false;

  constructor (public $scope: IExecutionDetailsScope,
               protected $stateParams: StateParams,
               protected executionDetailsSectionService: ExecutionDetailsSectionService) {
    super($scope, $stateParams, executionDetailsSectionService);
  }

  public $onInit(): void {
    super.$onInit();
    this.setScriptFailureFlag();
  }

  private setScriptFailureFlag(): void {
    const stage: IStage = this.$scope.stage;
    if (stage.isFailed && !stage.failureMessage && get(stage.context, 'buildInfo.result') === 'FAILURE') {
      this.scriptRanAndFailed = true;
    }
  }
}

export const SCRIPT_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.core.pipeline.stages.script.executionDetails.controller';
module(SCRIPT_EXECUTION_DETAILS_CONTROLLER, []).controller('ScriptExecutionDetailsCtrl', ScriptExecutionDetailsCtrl);
