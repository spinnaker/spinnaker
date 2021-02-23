import { StateParams } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';

import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  ExecutionDetailsSectionService,
} from '../../../details/executionDetailsSection.service';

export class WerckerExecutionDetailsCtrl implements IController {
  public configSections = ['werckerConfig', 'taskStatus'];
  public detailsSection: string;
  public failureMessage: string;
  public stage: any;

  public static $inject = ['$stateParams', 'executionDetailsSectionService', '$scope'];
  constructor(
    private $stateParams: StateParams,
    private executionDetailsSectionService: ExecutionDetailsSectionService,
    private $scope: IScope,
  ) {
    this.stage = this.$scope.stage;
    this.initialize();
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
  }

  public initialized(): void {
    this.detailsSection = this.$stateParams.details ?? '';
    this.failureMessage = this.getFailureMessage();
  }

  private getFailureMessage(): string {
    let failureMessage = this.stage.failureMessage;
    const context = this.stage.context || {};
    const buildInfo = context.buildInfo || {};
    const testResults: Array<{ failCount: number }> = buildInfo.testResults ?? [];
    const failingTests = testResults.filter((results) => results.failCount > 0);
    const failingTestCount = failingTests.reduce((acc, results) => acc + results.failCount, 0);
    if (buildInfo.result === 'FAILURE') {
      failureMessage = 'Build failed.';
    }
    if (failingTestCount) {
      failureMessage = `${failingTestCount} test${failingTestCount > 1 ? 's' : ''} failed.`;
    }
    return failureMessage;
  }

  private initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.configSections, () => this.initialized());
  }
}

export const WERCKER_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.core.pipeline.stage.wercker.executionDetails.controller';
module(WERCKER_EXECUTION_DETAILS_CONTROLLER, [EXECUTION_DETAILS_SECTION_SERVICE]).controller(
  'WerckerExecutionDetailsCtrl',
  WerckerExecutionDetailsCtrl,
);
