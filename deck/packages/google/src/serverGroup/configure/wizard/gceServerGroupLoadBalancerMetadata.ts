import type {
  GceLoadBalancerMetadataKey,
  IGceServerGroupCommand,
  ILoadBalancerMetadataReference,
} from './GceServerGroupWizard.types';
import { GCE_GLOBAL_LOAD_BALANCER_NAMES, GCE_REGIONAL_LOAD_BALANCER_NAMES } from './GceServerGroupWizard.types';

const METADATA_KEYS: GceLoadBalancerMetadataKey[] = [GCE_GLOBAL_LOAD_BALANCER_NAMES, GCE_REGIONAL_LOAD_BALANCER_NAMES];

export function metadataValues(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && Boolean(item));
  }
  return typeof value === 'string'
    ? value
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean)
    : [];
}

export function loadBalancerSelectionName(loadBalancer: unknown): string {
  if (typeof loadBalancer === 'string') {
    return loadBalancer;
  }
  return loadBalancer &&
    typeof loadBalancer === 'object' &&
    'name' in loadBalancer &&
    typeof loadBalancer.name === 'string'
    ? loadBalancer.name
    : '';
}

export function getSelectedLoadBalancerSelectionNames(command: IGceServerGroupCommand): string[] {
  const names: string[] = (command.loadBalancers || [])
    .map((loadBalancer: unknown) => loadBalancerSelectionName(loadBalancer))
    .filter(Boolean);
  return Array.from(new Set(names)).sort();
}

function sortedStringArraysEqual(left: string[], right: string[]): boolean {
  if (left.length !== right.length) {
    return false;
  }
  return left.every((value, index) => value === right[index]);
}

export function areLoadBalancerSelectionsChanged(command: IGceServerGroupCommand): boolean {
  const initialLoadBalancers = command.viewState?.initialLoadBalancers;
  if (!Array.isArray(initialLoadBalancers)) {
    return false;
  }
  return !sortedStringArraysEqual(getSelectedLoadBalancerSelectionNames(command), [...initialLoadBalancers].sort());
}

export function aggregateLoadBalancerSelectionMetadata(
  selectionMetadata: Record<string, ILoadBalancerMetadataReference> | undefined,
): Record<GceLoadBalancerMetadataKey, string[]> {
  return (Object.values(selectionMetadata || {}) as ILoadBalancerMetadataReference[]).reduce(
    (attributed, reference) => ({
      ...attributed,
      [reference.key]: Array.from(new Set([...(attributed[reference.key] || []), ...reference.names])),
    }),
    {
      [GCE_GLOBAL_LOAD_BALANCER_NAMES]: [],
      [GCE_REGIONAL_LOAD_BALANCER_NAMES]: [],
    } as Record<GceLoadBalancerMetadataKey, string[]>,
  );
}

export function getFlatLoadBalancerMetadata(
  command: IGceServerGroupCommand,
): Record<GceLoadBalancerMetadataKey, string[]> {
  const loadBalancerMetadata = command.loadBalancerMetadata || {};
  return {
    [GCE_GLOBAL_LOAD_BALANCER_NAMES]: metadataValues(loadBalancerMetadata[GCE_GLOBAL_LOAD_BALANCER_NAMES]),
    [GCE_REGIONAL_LOAD_BALANCER_NAMES]: metadataValues(loadBalancerMetadata[GCE_REGIONAL_LOAD_BALANCER_NAMES]),
  };
}

export function getUnattributedLoadBalancerMetadata(
  command: IGceServerGroupCommand,
): Record<GceLoadBalancerMetadataKey, string[]> {
  const flatMetadata = getFlatLoadBalancerMetadata(command);
  const attributedMetadata = aggregateLoadBalancerSelectionMetadata(command.loadBalancerSelectionMetadata);
  return METADATA_KEYS.reduce(
    (unattributed, key) => ({
      ...unattributed,
      [key]: flatMetadata[key].filter((name) => !attributedMetadata[key].includes(name)),
    }),
    {} as Record<GceLoadBalancerMetadataKey, string[]>,
  );
}

export function hasUnattributedLoadBalancerMetadata(command: IGceServerGroupCommand): boolean {
  const unattributedMetadata = getUnattributedLoadBalancerMetadata(command);
  return METADATA_KEYS.some((key) => unattributedMetadata[key].length > 0);
}

export const UNATTRIBUTED_LOAD_BALANCER_METADATA_ERROR =
  'Saved load balancer metadata cannot be mapped to the current selection. Re-select the load balancers or clear the saved metadata before submitting.';

export function validateLoadBalancerMetadataAttribution(command: IGceServerGroupCommand): string | undefined {
  if (!hasUnattributedLoadBalancerMetadata(command)) {
    return undefined;
  }
  if (!areLoadBalancerSelectionsChanged(command)) {
    return undefined;
  }
  return UNATTRIBUTED_LOAD_BALANCER_METADATA_ERROR;
}

export function selectionMetadataExplainsUnavailableLoadBalancer(
  selectionName: string,
  selectionMetadata: Record<string, ILoadBalancerMetadataReference> | undefined,
): boolean {
  const reference = selectionMetadata?.[selectionName];
  if (!reference?.names?.length) {
    return false;
  }
  return (
    reference.key === GCE_REGIONAL_LOAD_BALANCER_NAMES ||
    reference.loadBalancerType === 'HTTP' ||
    reference.loadBalancerType === 'SSL' ||
    reference.loadBalancerType === 'TCP'
  );
}
