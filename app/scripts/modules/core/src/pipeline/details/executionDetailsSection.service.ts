import { StateParams, StateService } from '@uirouter/angularjs';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { ITimeoutService, module } from 'angular';

export class ExecutionDetailsSectionService {
  private pendingOnComplete: PromiseLike<any>;

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
      // Wrapping in a $timeout because for a React stage, this block is executed during a transitionSuccess hook
      // meaning there is no location record to replace yet. Otherwise we incorrectly replace the previous record.
      this.$timeout(() => {
        // use { location: 'replace' } to overwrite the invalid browser history state
        this.$state.go('.', { details }, { location: 'replace' });
      });
    }
    if (onComplete) {
      this.pendingOnComplete = this.$timeout(onComplete);
    }
  }
}

export const EXECUTION_DETAILS_SECTION_SERVICE = 'spinnaker.executionDetails.section.service';
module(EXECUTION_DETAILS_SECTION_SERVICE, [UIROUTER_ANGULARJS]).service(
  'executionDetailsSectionService',
  ExecutionDetailsSectionService,
);
