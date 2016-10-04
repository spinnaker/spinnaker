import {module} from 'angular';

export class ExecutionDetailsSectionService {

  static get $inject() { return ['$stateParams', '$state', '$q', '$timeout']; }

  public constructor(private $stateParams: angular.ui.IStateParamsService,
                     private $state: angular.ui.IStateService,
                     private $q: ng.IQService,
                     private $timeout: ng.ITimeoutService) {}

  private sectionIsValid(availableSections: string[]): boolean {
    return availableSections.includes(this.$stateParams['details']);
  }

  public synchronizeSection(availableSections: string[], onComplete: () => any): void {
    if (!this.$state.includes('**.execution')) {
      return;
    }
    let details: any = this.$stateParams['details'] || availableSections[0];
    if (!availableSections.includes(details)) {
      details = availableSections[0];
    }
    if (!this.sectionIsValid(availableSections)) {
      // use { location: 'replace' } to overwrite the invalid browser history state
      this.$state.go('.', { details: details}, { location: 'replace' });
    }
    if (onComplete) {
      this.$timeout(onComplete);
    }
  }
}

const moduleName = 'spinnaker.executionDetails.section.service';

module(moduleName, [
  require('angular-ui-router'),
]).service('executionDetailsSectionService', ExecutionDetailsSectionService);

export default moduleName;
