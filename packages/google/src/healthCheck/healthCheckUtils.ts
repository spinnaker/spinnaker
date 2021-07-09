import { IGceHealthCheck, IGceHealthCheckKind } from '../domain';

export interface IGceHealthCheckOption {
  displayName: string;
  kind: IGceHealthCheckKind;
  name: string;
  selfLink: string;
}

export function parseHealthCheckUrl(url: string): { healthCheckName: string; healthCheckKind: IGceHealthCheckKind } {
  const healthCheckPathParts = url.split('/');
  if (healthCheckPathParts.length < 2) {
    throw new Error(`Health check url ${url} missing expected segments.`);
  }
  const healthCheckName = healthCheckPathParts[healthCheckPathParts.length - 1];
  const healthCheckKind = healthCheckPathParts[healthCheckPathParts.length - 2].slice(0, -1);
  return {
    healthCheckName,
    healthCheckKind: healthCheckKind as IGceHealthCheckKind,
  };
}

export function getHealthCheckOptions(healthChecks: IGceHealthCheck[]): IGceHealthCheckOption[] {
  const duplicateNames = getDuplicateHealthCheckNames(healthChecks);
  return healthChecks.map((hc) => {
    const isNameDupe = duplicateNames.has(hc.name);
    return {
      displayName: isNameDupe ? `${hc.name} (${hc.kind})` : hc.name,
      kind: hc.kind,
      name: hc.name,
      selfLink: hc.selfLink,
    };
  });
}

export function getDuplicateHealthCheckNames(healthChecks: IGceHealthCheck[]): Set<string> {
  const allNames = new Set<string>();
  const duplicateNames = new Set<string>();
  healthChecks.forEach((hc) => {
    if (allNames.has(hc.name)) {
      duplicateNames.add(hc.name);
    } else {
      allNames.add(hc.name);
    }
  });
  return duplicateNames;
}
