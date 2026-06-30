import type { IComponentOptions, IController } from 'angular';
import { module } from 'angular';
import { chain, get, has, intersection, set, without } from 'lodash';

import type { IGceBackendService, INamedPort } from '../../../../domain';
import { GceHttpLoadBalancerUtils } from '../../../../loadBalancer/httpLoadBalancerUtils.service';

import './loadBalancingPolicySelector.component.less';

class GceLoadBalancingPolicySelectorController implements IController {
  public maxPort = 65535;
  public command: any;
  [key: string]: any;
  public backendServices: IGceBackendService[];

  public static $inject = ['gceBackendServiceReader', '$q'];
  constructor(private gceBackendServiceReader: any, private $q: any) {}

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
     *   - If we have only passthrough load balancers, our balancing mode can be CONNECTION or UTILIZATION.
     *   - If we have both, only UTILIZATION.
     * */
    if (has(this, 'command.backingData.filtered.loadBalancerIndex')) {
      const index = this.command.backingData.filtered.loadBalancerIndex;
      const selected = this.command.loadBalancers;

      const hasPassthrough = selected.find((loadBalancer: any) =>
        this.isPassthroughLoadBalancerType(get(index[loadBalancer], 'loadBalancerType')),
      );
      const hasHttp = selected.find((loadBalancer: any) =>
        this.isHttpFamilyLoadBalancerType(get(index[loadBalancer], 'loadBalancerType')),
      );
      if (hasPassthrough && hasHttp) {
        balancingModes = ['UTILIZATION'];
      } else if (hasPassthrough) {
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
    this.$q
      .all([
        this.gceBackendServiceReader.listBackendServices('globalBackendService'),
        this.gceBackendServiceReader.listBackendServices('regionBackendService'),
      ])
      .then(([globalServices, regionalServices]: IGceBackendService[][]) => {
        this.backendServices = globalServices.concat(regionalServices);
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

    const getThem = (backendServices: IGceBackendService[], loadBalancer: string): string[] => {
      const selectedLoadBalancer = index[loadBalancer];
      const loadBalancerType = get(selectedLoadBalancer, 'loadBalancerType');
      const isRegionalHttpLoadBalancer =
        loadBalancerType === 'INTERNAL_MANAGED' || loadBalancerType === 'EXTERNAL_MANAGED';
      const serviceMatchesScope = (service: IGceBackendService): boolean => {
        // Backend service names are scoped in GCP; match the selected LB's account, kind, and
        // region before using the service's portName as a named-port suggestion.
        const account = get(selectedLoadBalancer, 'account');
        if (service.account && account && service.account !== account) {
          return false;
        }
        if (loadBalancerType === 'HTTP') {
          return service.kind === 'globalBackendService';
        }
        if (isRegionalHttpLoadBalancer) {
          const region = get(selectedLoadBalancer, 'region');
          return (
            service.kind === 'regionBackendService' &&
            (!region || service.region === region || (service.selfLink || '').includes(`/regions/${region}/`))
          );
        }
        return true;
      };
      switch (loadBalancerType) {
        case 'SSL':
        case 'TCP':
        case 'GRPC':
        case 'HTTP2':
        case 'INTERNAL_MANAGED':
        case 'EXTERNAL_MANAGED':
        case 'HTTP': {
          const lbBackendServices: string[] = get(index[loadBalancer], 'backendServices');
          const filteredBackendServices = backendServices.filter(
            (service: IGceBackendService) => lbBackendServices.includes(service.name) && serviceMatchesScope(service),
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
      .flatMap((lbName: string) => getThem(this.backendServices || [], lbName))
      .uniq()
      .value();
  }

  private decimalToPercent(value: number): number {
    if (value === 0) {
      return 0;
    }
    return value ? Math.round(value * 100) : undefined;
  }

  private isHttpFamilyLoadBalancerType(loadBalancerType: string): boolean {
    return (
      GceHttpLoadBalancerUtils.HTTP_LOAD_BALANCER_TYPES.includes(loadBalancerType) ||
      loadBalancerType === 'HTTP2' ||
      loadBalancerType === 'GRPC'
    );
  }

  private isPassthroughLoadBalancerType(loadBalancerType: string): boolean {
    return loadBalancerType === 'SSL' || loadBalancerType === 'TCP' || loadBalancerType === 'REGIONAL_EXTERNAL_NETWORK';
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
