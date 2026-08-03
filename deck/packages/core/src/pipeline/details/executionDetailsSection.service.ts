import type { RawParams, StateService } from '@uirouter/core';

import type { CancellableTimeout, CancellableTimeoutPromise } from '../../utils/cancellableTimeout';

export class ExecutionDetailsSectionService {
  private pendingOnComplete: CancellableTimeoutPromise<any>;

  public constructor(
    private $stateParams: RawParams,
    private $state: StateService,
    private timeout: CancellableTimeout,
  ) {}

  private sectionIsValid(availableSections: string[]): boolean {
    return availableSections.includes(this.$stateParams.details);
  }

  public synchronizeSection(availableSections: string[], onComplete?: () => any): void {
    this.timeout.cancel(this.pendingOnComplete);
    if (!this.$state.includes('**.execution')) {
      return;
    }
    let details: any = this.$stateParams.details || availableSections[0];
    if (!availableSections.includes(details)) {
      details = availableSections[0];
    }
    if (!this.sectionIsValid(availableSections)) {
      // Defer navigation until the transition has created the location record that should be replaced.
      this.timeout(() => {
        // use { location: 'replace' } to overwrite the invalid browser history state
        this.$state.go('.', { details }, { location: 'replace' });
      });
    }
    if (onComplete) {
      this.pendingOnComplete = this.timeout(onComplete);
    }
  }
}
