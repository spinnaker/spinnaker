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
  name?: string;
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

export interface ILoadBalancingPolicyErrors {
  balancingMode?: string;
  capacityScaler?: string;
  maxConnectionsPerInstance?: string;
  maxRatePerInstance?: string;
  maxUtilization?: string;
  namedPorts?: Array<{ name?: string; port?: string }>;
}

interface ISelectedLoadBalancer extends ILoadBalancerData {
  unresolved: boolean;
}

export interface ILoadBalancingPolicyCompatibility {
  error?: string;
  modes: string[];
  status: 'compatible' | 'incompatible' | 'none' | 'unresolved';
}

function getLoadBalancerIndex(command: IGceServerGroupCommand): Record<string, ILoadBalancerData> {
  return (command.backingData?.filtered?.loadBalancerIndex || {}) as Record<string, ILoadBalancerData>;
}

function loadBalancerName(loadBalancer: unknown): string | undefined {
  if (typeof loadBalancer === 'string') {
    return loadBalancer;
  }
  if (loadBalancer && typeof (loadBalancer as ILoadBalancerData).name === 'string') {
    return (loadBalancer as ILoadBalancerData).name;
  }
  return undefined;
}

function getSelectedLoadBalancers(command: IGceServerGroupCommand): ISelectedLoadBalancer[] {
  const loadBalancerIndex = getLoadBalancerIndex(command);
  const selected = Array.isArray(command.loadBalancers) ? command.loadBalancers : [];
  const byName = new Map<string, ISelectedLoadBalancer>();
  selected.forEach((selection, index) => {
    const name = loadBalancerName(selection);
    const inline =
      selection && typeof selection === 'object' && (selection as ILoadBalancerData).loadBalancerType
        ? (selection as ILoadBalancerData)
        : undefined;
    const data = inline || (name ? loadBalancerIndex[name] : undefined);
    const key = name || `inline-${index}`;
    if (!byName.has(key)) {
      byName.set(key, { ...data, name, unresolved: !data?.loadBalancerType });
    }
  });
  return Array.from(byName.values());
}

export function getSelectedLoadBalancerNames(command: IGceServerGroupCommand): string[] {
  return getSelectedLoadBalancers(command)
    .map(({ name }) => name)
    .filter((name): name is string => Boolean(name));
}

function isHttpLoadBalancer(loadBalancer: ILoadBalancerData): boolean {
  return gceHttpLoadBalancerUtils.isHttpLoadBalancer({ ...loadBalancer, provider: 'gce' } as any);
}

function isConnectionProxyLoadBalancer(loadBalancer: ILoadBalancerData): boolean {
  return loadBalancer.loadBalancerType === 'SSL' || loadBalancer.loadBalancerType === 'TCP';
}

// Mirrors BasicGoogleDeployHandler.hasPassthroughBackend. Clouddriver writes these backends with a
// mode of its own choosing rather than the policy's, so they constrain the other families without
// offering a mode themselves.
function isPassthroughLoadBalancer(loadBalancer: ILoadBalancerData): boolean {
  return loadBalancer.loadBalancerType === 'REGIONAL_EXTERNAL_NETWORK' || loadBalancer.loadBalancerType === 'INTERNAL';
}

export function getLoadBalancingPolicyCompatibility(
  command: IGceServerGroupCommand,
): ILoadBalancingPolicyCompatibility {
  const selectedLoadBalancers = getSelectedLoadBalancers(command);
  if (!selectedLoadBalancers.length) {
    return { modes: [], status: 'none' };
  }
  const unresolvedLoadBalancers = selectedLoadBalancers.filter(({ unresolved }) => unresolved);
  const selectedLoadBalancerData = selectedLoadBalancers.filter(({ unresolved }) => !unresolved);
  const hasPassthrough = selectedLoadBalancerData.some(isPassthroughLoadBalancer);
  // Only a passthrough backend makes the required mode depend on the other family, so only then
  // does an unidentifiable selection leave us unable to answer.
  if (unresolvedLoadBalancers.length && selectedLoadBalancerData.length && hasPassthrough) {
    return {
      error:
        'Load balancer compatibility cannot determine a balancing mode while a selected load balancer type is unavailable.',
      modes: [],
      status: 'unresolved',
    };
  }

  const hasPolicyBackedProxy = selectedLoadBalancerData.some(
    (loadBalancer) => isHttpLoadBalancer(loadBalancer) || isConnectionProxyLoadBalancer(loadBalancer),
  );
  const modeSets = selectedLoadBalancerData
    .map((loadBalancer) => {
      if (isPassthroughLoadBalancer(loadBalancer)) {
        return [];
      }
      if (isHttpLoadBalancer(loadBalancer)) {
        return HTTP_BALANCING_MODES;
      }
      return CONNECTION_BALANCING_MODES;
    })
    .filter((modes) => modes.length > 0);
  if (!modeSets.length) {
    if (unresolvedLoadBalancers.length) {
      const persistedMode = (command.loadBalancingPolicy as ILoadBalancingPolicy | undefined)?.balancingMode;
      return persistedMode ? { modes: [persistedMode], status: 'compatible' } : { modes: [], status: 'none' };
    }
    return { modes: [], status: 'none' };
  }
  let balancingModes = modeSets
    .slice(1)
    .reduce((modes, nextModes) => modes.filter((mode) => nextModes.includes(mode)), modeSets[0]);
  if (hasPassthrough && hasPolicyBackedProxy) {
    balancingModes = balancingModes.filter((mode) => mode !== 'UTILIZATION');
  }
  if (!balancingModes.length) {
    return {
      error:
        'The selected load balancers have no compatible balancing mode. HTTP load balancers require RATE with regional passthrough load balancers, while SSL/TCP load balancers require CONNECTION.',
      modes: [],
      status: 'incompatible',
    };
  }
  return { modes: balancingModes, status: 'compatible' };
}

export function getBalancingModes(command: IGceServerGroupCommand): string[] {
  return getLoadBalancingPolicyCompatibility(command).modes;
}

export function resolveLoadBalancingPolicy(command: IGceServerGroupCommand): ILoadBalancingPolicy | undefined {
  const compatibility = getLoadBalancingPolicyCompatibility(command);
  const existing = command.loadBalancingPolicy as ILoadBalancingPolicy | undefined;
  if (compatibility.status === 'incompatible' || compatibility.status === 'unresolved') {
    return existing;
  }
  if (!compatibility.modes.length) {
    return undefined;
  }
  const defaultBalancingMode = compatibility.modes.includes(DEFAULT_LOAD_BALANCING_POLICY.balancingMode)
    ? DEFAULT_LOAD_BALANCING_POLICY.balancingMode
    : compatibility.modes[0];
  const balancingMode =
    existing?.balancingMode && compatibility.modes.includes(existing.balancingMode)
      ? existing.balancingMode
      : defaultBalancingMode;
  const policy: ILoadBalancingPolicy = {
    ...existing,
    balancingMode,
    capacityScaler: existing?.capacityScaler ?? DEFAULT_LOAD_BALANCING_POLICY.capacityScaler,
    namedPorts: existing?.namedPorts ?? DEFAULT_LOAD_BALANCING_POLICY.namedPorts.map((namedPort) => ({ ...namedPort })),
  };
  if (existing?.balancingMode !== balancingMode) {
    const selectedLimitField =
      balancingMode === 'RATE'
        ? 'maxRatePerInstance'
        : balancingMode === 'CONNECTION'
        ? 'maxConnectionsPerInstance'
        : 'maxUtilization';
    MODE_LIMIT_FIELDS.filter((field) => field !== selectedLimitField).forEach((field) => delete policy[field]);
  }
  if (balancingMode === 'UTILIZATION' && policy.maxUtilization === undefined) {
    policy.maxUtilization = DEFAULT_LOAD_BALANCING_POLICY.maxUtilization;
  }
  return policy;
}

export function validateLoadBalancingPolicy(command: IGceServerGroupCommand): ILoadBalancingPolicyErrors | undefined {
  const compatibility = getLoadBalancingPolicyCompatibility(command);
  if (compatibility.error) {
    return { balancingMode: compatibility.error };
  }
  if (compatibility.status === 'none') {
    return undefined;
  }

  const existing = command.loadBalancingPolicy as ILoadBalancingPolicy | undefined;
  const policy = existing?.balancingMode === '' ? existing : resolveLoadBalancingPolicy(command);
  if (!policy) {
    return undefined;
  }
  const errors: ILoadBalancingPolicyErrors = {};
  if (!policy.balancingMode || !compatibility.modes.includes(policy.balancingMode)) {
    errors.balancingMode = 'Select a balancing mode supported by the selected load balancers.';
  }
  if (!isValidBoundedValue(policy.capacityScaler, command, 0, 1)) {
    errors.capacityScaler = 'Capacity must be between 0 and 100%.';
  }

  const namedPortErrors = (policy.namedPorts || []).map(({ name, port }) => {
    const namedPortError: { name?: string; port?: string } = {};
    if (!name?.trim()) {
      namedPortError.name = 'Port name required.';
    }
    if (!isValidInteger(port, command, 1, 65535)) {
      namedPortError.port = 'Port must be an integer between 1 and 65535.';
    }
    return namedPortError;
  });
  if (namedPortErrors.some((namedPortError) => Object.keys(namedPortError).length)) {
    errors.namedPorts = namedPortErrors;
  }
  if (policy.balancingMode === 'RATE' && !isValidMinimum(policy.maxRatePerInstance, command, 0)) {
    errors.maxRatePerInstance = 'Max rate must be a finite number greater than or equal to zero.';
  }
  if (policy.balancingMode === 'CONNECTION' && !isValidMinimum(policy.maxConnectionsPerInstance, command, 0)) {
    errors.maxConnectionsPerInstance = 'Max connections must be a finite number greater than or equal to zero.';
  }
  if (policy.balancingMode === 'UTILIZATION' && !isValidBoundedValue(policy.maxUtilization, command, 0, 1)) {
    errors.maxUtilization = 'Max utilization must be between 0 and 100%.';
  }
  return Object.keys(errors).length ? errors : undefined;
}

function isPipelineMode(command: IGceServerGroupCommand): boolean {
  return command.viewState.mode === 'createPipeline' || command.viewState.mode === 'editPipeline';
}

function isExpression(value: unknown): boolean {
  return typeof value === 'string' && /^\s*\$\{.+\}\s*$/.test(value);
}

function isValidBoundedValue(
  value: unknown,
  command: IGceServerGroupCommand,
  minimum: number,
  maximum: number,
): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  if (!hasNumericValue(value)) {
    return false;
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue >= minimum && numericValue <= maximum;
}

function isValidMinimum(value: unknown, command: IGceServerGroupCommand, minimum: number): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  if (!hasNumericValue(value)) {
    return false;
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue >= minimum;
}

function isValidInteger(value: unknown, command: IGceServerGroupCommand, minimum: number, maximum: number): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  if (!hasNumericValue(value)) {
    return false;
  }
  const numericValue = Number(value);
  return (
    Number.isFinite(numericValue) &&
    Number.isInteger(numericValue) &&
    numericValue >= minimum &&
    numericValue <= maximum
  );
}

function hasNumericValue(value: unknown): boolean {
  return value !== null && value !== undefined && (typeof value !== 'string' || Boolean(value.trim()));
}
