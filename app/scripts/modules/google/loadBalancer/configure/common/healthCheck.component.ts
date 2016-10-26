import {module} from 'angular';
import {IGceHealthCheck} from '../../../domain/index';
import {IHealthCheckMap} from '../internal/gceCreateInternalLoadBalancer.controller';

class HealthCheckCreateCtrl implements ng.IComponentController {
  healthCheck: IGceHealthCheck;
  healthCheckPlaceholder: IGceHealthCheck;
  existingHealthCheckMap: IHealthCheckMap;
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
        this.existingHealthCheckMap[this.credentials][this.healthCheck.healthCheckType];
      this.editExisting = true;
    } else {
      this.existingHealthChecksForProtocol = this.existingHealthCheckMap[this.credentials]['TCP'];
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
      _.get<{}, IGceHealthCheck[]>(this, ['existingHealthCheckMap', this.credentials, this.healthCheck.healthCheckType]);
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
    existingHealthCheckMap: '<',
    existingHealthCheckNames: '<',
  };
  templateUrl: string = require('./healthCheck.component.html');
  controller: ng.IComponentController = HealthCheckCreateCtrl;
}

const gceHealthCheckCreate = 'spinnaker.gce.healthCheckCreate.component';

module(gceHealthCheckCreate, [])
  .component('gceHealthCheckCreate', new HealthCheckCreateComponent());

export default gceHealthCheckCreate;
