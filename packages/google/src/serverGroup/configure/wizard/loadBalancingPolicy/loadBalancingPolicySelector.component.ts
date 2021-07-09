import { IComponentOptions, IController, module } from 'angular';
import { chain, get, has, intersection, set, without } from 'lodash';

import { IGceBackendService, INamedPort } from '../../../../domain';

import './loadBalancingPolicySelector.component.less';

class GceLoadBalancingPolicySelectorController implements IController {
  public maxPort = 65535;
  public command: any;
  [key: string]: any;
  public globalBackendServices: IGceBackendService[];

  public static $inject = ['gceBackendServiceReader'];
  constructor(private gceBackendServiceReader: any) {}

  public setModel(propertyName: string, viewValue: number): void {
    set(this, propertyName, viewValue / 100);
  }

  public setView(propertyName: string, modelValue: number): void {
    this[propertyName] = this.decimalToPercent(modelValue);
  }

  public onBalancingModeChange(mode: string): void {
    const keys: string[] = ['maxUtilization', 'maxRatePerInstance', 'maxConnectionsPerInstance'];
    let toDelete: string[] = [];
    switch (mode) {
      case 'RATE':
        toDelete = without(keys, 'maxRatePerInstance');
        break;
      case 'UTILIZATION':
        toDelete = without(keys, 'maxUtilization');
        break;
      case 'CONNECTION':
        toDelete = without(keys, 'maxConnectionsPerInstance');
        break;
      default:
        break;
    }

    toDelete.forEach((key) => delete this.command.loadBalancingPolicy[key]);
  }

  public getBalancingModes(): string[] {
    let balancingModes: string[] = [];
    /*
     * Three cases:
     *   - If we have only HTTP(S) load balancers, our balancing mode can be RATE or UTILIZATION.
     *   - If we have only SSL/TCP load balancers, our balancing mode can be CONNECTION or UTILIZATION.
     *   - If we have both, only UTILIZATION.
     * */
    if (has(this, 'command.backingData.filtered.loadBalancerIndex')) {
      const index = this.command.backingData.filtered.loadBalancerIndex;
      const selected = this.command.loadBalancers;

      const hasSsl = selected.find((loadBalancer: any) => get(index[loadBalancer], 'loadBalancerType') === 'SSL');
      const hasTcp = selected.find((loadBalancer: any) => get(index[loadBalancer], 'loadBalancerType') === 'TCP');
      const hasHttp = selected.find((loadBalancer: any) => get(index[loadBalancer], 'loadBalancerType') === 'HTTP');

      if ((hasSsl || hasTcp) && hasHttp) {
        balancingModes = ['UTILIZATION'];
      } else if (hasSsl || hasTcp) {
        balancingModes = ['CONNECTION', 'UTILIZATION'];
      } else {
        balancingModes = ['RATE', 'UTILIZATION'];
      }
    }

    if (!balancingModes.includes(get(this.command, 'loadBalancingPolicy.balancingMode') as string)) {
      set(this.command, 'loadBalancingPolicy.balancingMode', balancingModes[0]);
    }
    return balancingModes;
  }

  public $onInit(): void {
    this.gceBackendServiceReader.listBackendServices('globalBackendService').then((services: IGceBackendService[]) => {
      this.globalBackendServices = services;
    });
  }

  public $onDestroy(): void {
    delete this.command.loadBalancingPolicy;
  }

  public addNamedPort() {
    if (!get(this.command, 'loadBalancingPolicy.namedPorts')) {
      set(this.command, 'loadBalancingPolicy.namedPorts', []);
    }

    this.command.loadBalancingPolicy.namedPorts.push({ name: '', port: 80 });
  }

  public removeNamedPort(index: number) {
    this.command.loadBalancingPolicy.namedPorts.splice(index, 1);
  }

  public getPortNames(): string[] {
    const index = this.command.backingData.filtered.loadBalancerIndex;
    const selected = this.command.loadBalancers;
    const inUsePortNames = this.command.loadBalancingPolicy.namedPorts.map((namedPort: INamedPort) => namedPort.name);

    const getThem = (globalBackendServices: IGceBackendService[], loadBalancer: string): string[] => {
      switch (get(index[loadBalancer], 'loadBalancerType')) {
        case 'SSL':
        case 'TCP':
        case 'HTTP': {
          const lbBackendServices: string[] = get(index[loadBalancer], 'backendServices');
          const filteredBackendServices = globalBackendServices.filter((service: IGceBackendService) =>
            lbBackendServices.includes(service.name),
          );
          const portNames = filteredBackendServices.map((service: IGceBackendService) => service.portName);
          const portNameIntersection = intersection(portNames, inUsePortNames);
          return portNames.filter((portName) => !portNameIntersection.includes(portName));
        }
        default:
          return [];
      }
    };

    return chain(selected)
      .flatMap((lbName: string) => getThem(this.globalBackendServices, lbName))
      .uniq()
      .value();
  }

  private decimalToPercent(value: number): number {
    if (value === 0) {
      return 0;
    }
    return value ? Math.round(value * 100) : undefined;
  }
}

const gceLoadBalancingPolicySelectorComponent: IComponentOptions = {
  bindings: {
    command: '=',
  },
  controller: GceLoadBalancingPolicySelectorController,
  templateUrl: require('./loadBalancingPolicySelector.component.html'),
};

export const GCE_LOAD_BALANCING_POLICY_SELECTOR = 'spinnaker.gce.loadBalancingPolicy.selector.component';

module(GCE_LOAD_BALANCING_POLICY_SELECTOR, []).component(
  'gceLoadBalancingPolicySelector',
  gceLoadBalancingPolicySelectorComponent,
);
