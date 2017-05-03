import { module } from 'angular';
import { StateParams, StateService } from 'angular-ui-router';

export class ExecutionDetailsSectionService {

  private pendingOnComplete: ng.IPromise<any>;

  static get $inject() { return ['$stateParams', '$state', '$timeout']; }

  public constructor(private $stateParams: StateParams,
                     private $state: StateService,
                     private $timeout: ng.ITimeoutService) {}

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
      this.$state.go('.', { details: details}, { location: 'replace' });
    }
    if (onComplete) {
      this.pendingOnComplete = this.$timeout(onComplete);
    }
  }
}

export const EXECUTION_DETAILS_SECTION_SERVICE = 'spinnaker.executionDetails.section.service';
module(EXECUTION_DETAILS_SECTION_SERVICE, [
  require('angular-ui-router').default,
]).service('executionDetailsSectionService', ExecutionDetailsSectionService);
