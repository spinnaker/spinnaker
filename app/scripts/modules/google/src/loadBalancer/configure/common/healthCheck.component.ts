import { IController, module } from 'angular';
import { get } from 'lodash';

import { IGceHealthCheck } from '../../../domain/index';

class HealthCheckCreateCtrl implements IController {
  public healthCheck: IGceHealthCheck;
  public healthCheckPlaceholder: IGceHealthCheck;
  public healthChecksByAccountAndType: { [account: string]: { [healthCheckType: string]: IGceHealthCheck[] } };
  public editExisting = false;
  public existingHealthChecksForProtocol: IGceHealthCheck[];
  public existingHealthCheckNames: string[];
  public credentials: string;
  public max = Number.MAX_SAFE_INTEGER;

  public static $inject = ['$scope'];
  constructor(private $scope: ng.IScope) {}

  public $onInit(): void {
    if (this.healthCheck.name) {
      this.healthCheckPlaceholder = this.healthCheck;
      this.existingHealthChecksForProtocol = this.healthChecksByAccountAndType[this.credentials][
        this.healthCheck.healthCheckType
      ];
      this.editExisting = true;
    } else {
      this.existingHealthChecksForProtocol = this.healthChecksByAccountAndType[this.credentials]['TCP'];
    }

    this.$scope.$watch('$ctrl.credentials', () => this.setExistingHealthChecksForProtocol());
  }

  public onProtocolChange(): void {
    delete this.healthCheck.name;
    delete this.healthCheckPlaceholder;
    this.setExistingHealthChecksForProtocol();
  }

  public setExistingHealthChecksForProtocol() {
    this.existingHealthChecksForProtocol =
      get<{}, IGceHealthCheck[]>(this, [
        'healthChecksByAccountAndType',
        this.credentials,
        this.healthCheck.healthCheckType,
      ]) || [];

    if (!this.existingHealthChecksForProtocol.find((healthCheck) => healthCheck.name === this.healthCheck.name)) {
      delete this.healthCheck.name;
      delete this.healthCheckPlaceholder;
    }
  }

  public toggleEditExisting(): void {
    this.editExisting = !this.editExisting;
    delete this.healthCheck.name;
    delete this.healthCheckPlaceholder;
  }

  public onHealthCheckSelect(selectedHealthCheck: IGceHealthCheck): void {
    Object.assign(this.healthCheck, selectedHealthCheck);
  }

  public onHealthCheckNameChange(typedName: string): void {
    this.healthCheck.name = typedName;
  }
}

const healthCheckCreateComponent: ng.IComponentOptions = {
  bindings: {
    healthCheck: '=',
    credentials: '<',
    healthChecksByAccountAndType: '<',
    existingHealthCheckNames: '<',
  },
  templateUrl: require('./healthCheck.component.html'),
  controller: HealthCheckCreateCtrl,
};

export const GCE_HEALTH_CHECK_SELECTOR_COMPONENT = 'spinnaker.gce.healthCheckSelector.component';

module(GCE_HEALTH_CHECK_SELECTOR_COMPONENT, []).component('gceHealthCheckSelector', healthCheckCreateComponent);
