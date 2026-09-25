import { interpolate as coreInterpolate } from '@spinnaker/core';

export function interpolate(template: string, context: Record<string, any>): string {
  return coreInterpolate(template, context);
}

export function extractDataFromLoadBalancerManifest(manifest: any): any {
  const displayName = manifest?.manifest?.metadata?.name;
  const namespace = manifest?.manifest?.metadata?.namespace;
  if (!displayName || !namespace) {
    return null;
  }
  return {
    account: manifest.account,
    displayName,
    namespace,
  };
}
