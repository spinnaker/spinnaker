import { IPromise, ITimeoutService, module } from 'angular';
import { StateParams, StateService } from '@uirouter/angularjs';

export class ExecutionDetailsSectionService {
  private pendingOnComplete: IPromise<any>;

  public static $inject = ['$stateParams', '$state', '$timeout'];
  public constructor(
    private $stateParams: StateParams,
    private $state: StateService,
    private $timeout: ITimeoutService,
  ) {}

  private sectionIsValid(availableSections: string[]): boolean {
    return availableSections.includes(this.$stateParams.details);
  }

  public synchronizeSection(availableSections: string[], onComplete?: () => any): void {
    this.$timeout.cancel(this.pendingOnComplete);
    if (!this.$state.includes('**.execution')) {
      return;
    }
    let details: any = this.$stateParams.details || availableSections[0];
    if (!availableSections.includes(details)) {
      details = availableSections[0];
    }
    if (!this.sectionIsValid(availableSections)) {
      // use { location: 'replace' } to overwrite the invalid browser history state
      this.$state.go('.', { details }, { location: 'replace' });
    }
    if (onComplete) {
      this.pendingOnComplete = this.$timeout(onComplete);
    }
  }
}

export const EXECUTION_DETAILS_SECTION_SERVICE = 'spinnaker.executionDetails.section.service';
module(EXECUTION_DETAILS_SECTION_SERVICE, [require('@uirouter/angularjs').default]).service(
  'executionDetailsSectionService',
  ExecutionDetailsSectionService,
);
