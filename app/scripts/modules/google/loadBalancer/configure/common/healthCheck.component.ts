import {get} from 'lodash';
import {module} from 'angular';
import {IGceHealthCheck} from 'google/domain/index';

class HealthCheckCreateCtrl implements ng.IComponentController {
  healthCheck: IGceHealthCheck;
  healthCheckPlaceholder: IGceHealthCheck;
  healthChecksByAccountAndType: {[account: string]: {[healthCheckType: string]: IGceHealthCheck[]}};
  editExisting: boolean = false;
  existingHealthChecksForProtocol: IGceHealthCheck[];
  existingHealthCheckNames: string[];
  credentials: string;
  max: number = Number.MAX_SAFE_INTEGER;

  constructor (private $scope: ng.IScope) {}

  $onInit (): void {
    if (this.healthCheck.name) {
      this.healthCheckPlaceholder = this.healthCheck;
      this.existingHealthChecksForProtocol =
        this.healthChecksByAccountAndType[this.credentials][this.healthCheck.healthCheckType];
      this.editExisting = true;
    } else {
      this.existingHealthChecksForProtocol = this.healthChecksByAccountAndType[this.credentials]['TCP'];
    }

    this.$scope.$watch('$ctrl.credentials', () => this.setExistingHealthChecksForProtocol());
  }

  onProtocolChange (): void {
    delete this.healthCheck.name;
    delete this.healthCheckPlaceholder;
    this.setExistingHealthChecksForProtocol();
  }

  setExistingHealthChecksForProtocol () {
    this.existingHealthChecksForProtocol =
      get<{}, IGceHealthCheck[]>(this, ['healthChecksByAccountAndType', this.credentials, this.healthCheck.healthCheckType]) || [];

    if (!this.existingHealthChecksForProtocol.find((healthCheck) => healthCheck.name === this.healthCheck.name)) {
      delete this.healthCheck.name;
      delete this.healthCheckPlaceholder;
    }
  }

  toggleEditExisting (): void {
    this.editExisting = !this.editExisting;
    delete this.healthCheck.name;
    delete this.healthCheckPlaceholder;
  }

  onHealthCheckSelect (selectedHealthCheck: IGceHealthCheck): void {
    Object.assign(this.healthCheck, selectedHealthCheck);
  }

  onHealthCheckNameChange (typedName: string): void {
    this.healthCheck.name = typedName;
  }
}

class HealthCheckCreateComponent implements ng.IComponentOptions {
  bindings: any = {
    healthCheck: '=',
    credentials: '<',
    healthChecksByAccountAndType: '<',
    existingHealthCheckNames: '<',
  };
  templateUrl: string = require('./healthCheck.component.html');
  controller: any = HealthCheckCreateCtrl;
}

export const GCE_HEALTH_CHECK_SELECTOR_COMPONENT = 'spinnaker.gce.healthCheckSelector.component';

module(GCE_HEALTH_CHECK_SELECTOR_COMPONENT, [])
  .component('gceHealthCheckSelector', new HealthCheckCreateComponent());

