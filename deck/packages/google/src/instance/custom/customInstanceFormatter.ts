import { GceCustomInstanceBuilderService } from './customInstanceBuilder.gce.service';

const customInstanceBuilder = new GceCustomInstanceBuilderService();
const EXTENDED_MEMORY_SUFFIX = '-ext';

/**
 * Formats a GCE machine type for display. Custom machine types such as `custom-2-4096` or
 * `n2-custom-4-8192-ext` are rendered as `N1 2 vCPU / 4 GB RAM`; predefined machine types are returned as-is.
 */
export function formatGceInstanceType(instanceType: string | undefined | null): string | undefined {
  if (!instanceType) {
    return undefined;
  }
  if (!instanceType.includes('custom-')) {
    return instanceType;
  }
  const extendedMemory = instanceType.endsWith(EXTENDED_MEMORY_SUFFIX);
  const baseType = extendedMemory ? instanceType.slice(0, -EXTENDED_MEMORY_SUFFIX.length) : instanceType;
  const { instanceFamily, vCpuCount, memory } = customInstanceBuilder.parseInstanceTypeString(baseType);
  if (!Number.isFinite(vCpuCount) || !Number.isFinite(memory)) {
    return instanceType;
  }
  return `${instanceFamily} ${vCpuCount} vCPU / ${memory} GB RAM${extendedMemory ? ' (extended)' : ''}`;
}
