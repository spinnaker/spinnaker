import type { IGceServerGroupCommand } from './GceServerGroupWizard.types';
import { GceHttpLoadBalancerUtils } from '../../../loadBalancer/httpLoadBalancerUtils.service';

const gceHttpLoadBalancerUtils = new GceHttpLoadBalancerUtils();

const HTTP_BALANCING_MODES = ['RATE', 'UTILIZATION'];
const CONNECTION_BALANCING_MODES = ['CONNECTION', 'UTILIZATION'];
const MODE_LIMIT_FIELDS = ['maxConnectionsPerInstance', 'maxRatePerInstance', 'maxUtilization'];
const DEFAULT_LOAD_BALANCING_POLICY = {
  balancingMode: 'UTILIZATION',
  capacityScaler: 1,
  maxUtilization: 0.8,
  namedPorts: [{ name: 'http', port: 80 }],
};

interface ILoadBalancerData {
  loadBalancerType?: string;
}

interface ILoadBalancingPolicy {
  [key: string]: any;
  balancingMode?: string;
  capacityScaler?: number | string;
  maxConnectionsPerInstance?: number | string;
  maxRatePerInstance?: number | string;
  maxUtilization?: number | string;
  namedPorts?: Array<{ name: string; port: number | string }>;
}

function uniqueStrings(values: unknown): string[] {
  if (!Array.isArray(values)) {
    return [];
  }
  return Array.from(new Set(values.filter((value): value is string => typeof value === 'string' && Boolean(value))));
}

function getLoadBalancerIndex(command: IGceServerGroupCommand): Record<string, ILoadBalancerData> {
  return (command.backingData?.filtered?.loadBalancerIndex || {}) as Record<string, ILoadBalancerData>;
}

export function getBalancingModes(command: IGceServerGroupCommand): string[] {
  const loadBalancerIndex = getLoadBalancerIndex(command);
  const selectedLoadBalancers = uniqueStrings(command.loadBalancers);
  const modeSets = selectedLoadBalancers
    .map((loadBalancerName) => loadBalancerIndex[loadBalancerName])
    .filter((loadBalancer): loadBalancer is ILoadBalancerData => Boolean(loadBalancer))
    .map((loadBalancer) => {
      if (loadBalancer.loadBalancerType === 'REGIONAL_EXTERNAL_NETWORK') {
        return [];
      }
      if (gceHttpLoadBalancerUtils.isHttpLoadBalancer({ ...loadBalancer, provider: 'gce' } as any)) {
        return HTTP_BALANCING_MODES;
      }
      return CONNECTION_BALANCING_MODES;
    })
    .filter((modes) => modes.length > 0);
  if (!modeSets.length) {
    if (
      selectedLoadBalancers.length > 0 &&
      selectedLoadBalancers.every(
        (loadBalancerName) => loadBalancerIndex[loadBalancerName]?.loadBalancerType === 'REGIONAL_EXTERNAL_NETWORK',
      )
    ) {
      return [];
    }
    const persistedMode = (command.loadBalancingPolicy as ILoadBalancingPolicy | undefined)?.balancingMode;
    return persistedMode ? [persistedMode] : [];
  }
  return modeSets.slice(1).reduce((modes, nextModes) => modes.filter((mode) => nextModes.includes(mode)), modeSets[0]);
}

export function resolveLoadBalancingPolicy(command: IGceServerGroupCommand): ILoadBalancingPolicy | undefined {
  const balancingModes = getBalancingModes(command);
  if (!balancingModes.length) {
    return undefined;
  }
  const existing = command.loadBalancingPolicy as ILoadBalancingPolicy | undefined;
  const balancingMode =
    existing?.balancingMode && balancingModes.includes(existing.balancingMode)
      ? existing.balancingMode
      : DEFAULT_LOAD_BALANCING_POLICY.balancingMode;
  const policy: ILoadBalancingPolicy = {
    ...existing,
    balancingMode,
    capacityScaler: existing?.capacityScaler ?? DEFAULT_LOAD_BALANCING_POLICY.capacityScaler,
    namedPorts: existing?.namedPorts ?? DEFAULT_LOAD_BALANCING_POLICY.namedPorts.map((namedPort) => ({ ...namedPort })),
  };
  if (existing?.balancingMode !== balancingMode) {
    MODE_LIMIT_FIELDS.forEach((field) => delete policy[field]);
  }
  if (balancingMode === 'UTILIZATION' && policy.maxUtilization === undefined) {
    policy.maxUtilization = DEFAULT_LOAD_BALANCING_POLICY.maxUtilization;
  }
  return policy;
}
