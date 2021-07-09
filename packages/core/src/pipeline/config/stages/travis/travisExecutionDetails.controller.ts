import { StateParams } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';

import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  ExecutionDetailsSectionService,
} from '../../../details/executionDetailsSection.service';

export class TravisExecutionDetailsCtrl implements IController {
  public configSections = ['travisConfig', 'taskStatus'];
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
    this.$scope.$watch('stage.refId', () => (this.stage = $scope.stage));
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

export const TRAVIS_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.core.pipeline.stage.travis.executionDetails.controller';
module(TRAVIS_EXECUTION_DETAILS_CONTROLLER, [EXECUTION_DETAILS_SECTION_SERVICE]).controller(
  'TravisExecutionDetailsCtrl',
  TravisExecutionDetailsCtrl,
);
