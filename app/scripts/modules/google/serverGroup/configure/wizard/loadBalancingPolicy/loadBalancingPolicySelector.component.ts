import _ from 'lodash';
import {module} from 'angular';
import './loadBalancingPolicySelector.component.less';

class GceLoadBalancingPolicySelectorController implements ng.IComponentController {

  public maxPort: number = 65535;
  public command: any;

  public setModel (propertyName: string, viewValue: number): void {
    _.set(this, propertyName, viewValue / 100);
  };

  public setView (propertyName:string , modelValue: number): void {
    this[propertyName] = this.decimalToPercent(modelValue);
  };

  public onBalancingModeChange (mode: string): void {
    if (mode === 'RATE') {
      delete this.command.loadBalancingPolicy.maxUtilization;
    } else if (mode === 'UTILIZATION') {
      delete this.command.loadBalancingPolicy.maxRatePerInstance;
    }
  }

  public $onInit (): void {
    if (!this.command.loadBalancingPolicy) {
      this.command.loadBalancingPolicy = {
        balancingMode: 'UTILIZATION'
      };
    }
  }

  public $onDestroy (): void {
    delete this.command.loadBalancingPolicy;
  }

  private decimalToPercent (value: number): number {
    if (value === 0) {
      return 0;
    }
    return value ? Math.round(value * 100) : undefined;
  }
}

class GceLoadBalancingPolicySelectorComponent implements ng.IComponentOptions {
  public bindings: any = {
    command: '='
  };
  public controller: ng.IComponentController = GceLoadBalancingPolicySelectorController;
  public templateUrl: string = require('./loadBalancingPolicySelector.component.html');
}

const moduleName = 'spinnaker.gce.loadBalancingPolicy.selector.component';

module(moduleName, [])
  .component('gceLoadBalancingPolicySelector', new GceLoadBalancingPolicySelectorComponent());

export default moduleName;
