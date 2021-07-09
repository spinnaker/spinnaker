import { IGceHealthCheck, IGceHealthCheckKind } from '../domain';
import { parseHealthCheckUrl, getHealthCheckOptions, getDuplicateHealthCheckNames } from './healthCheckUtils';

describe('Health check display utils', () => {
  let healthChecks: IGceHealthCheck[];
  beforeEach(() => {
    healthChecks = getSampleHealthChecks();
  });
  describe('parseHealthCheckUrl', () => {
    it('extracts name and kind from health check url', () => {
      healthChecks.forEach((hc) => {
        expect(parseHealthCheckUrl(hc.selfLink)).toEqual({
          healthCheckName: hc.name,
          healthCheckKind: hc.kind,
        });
      });
    });
  });
  describe('getHealthCheckOptions', () => {
    it('adds appropriate displayName to each health check, including kind when name is duplicate', () => {
      expect(getHealthCheckOptions(healthChecks).map((hc) => hc.displayName)).toEqual([
        'hello (healthCheck)',
        'hello (httpsHealthCheck)',
        'ping',
      ]);
    });
  });
  describe('getDuplicateHealthCheckNames', () => {
    it('returns list of names that occur more than once in list of health checks', () => {
      const duplicates = getDuplicateHealthCheckNames(healthChecks);
      expect(duplicates.size).toEqual(1);
      expect(duplicates.has('hello')).toBeTruthy();
    });
  });
});

function getSampleHealthChecks(): IGceHealthCheck[] {
  return [
    {
      account: 'my-gce-account',
      checkIntervalSec: 1,
      healthCheckType: 'HTTP',
      healthyThreshold: 1,
      kind: IGceHealthCheckKind.healthCheck,
      name: 'hello',
      port: 8080,
      requestPath: '/hello',
      selfLink: 'https://www.googleapis.com/compute/beta/projects/my-project/global/healthChecks/hello',
      timeoutSec: 1,
      unhealthyThreshold: 1,
    },
    {
      account: 'my-gce-account',
      checkIntervalSec: 1,
      healthCheckType: 'HTTPS',
      healthyThreshold: 1,
      kind: IGceHealthCheckKind.httpsHealthCheck,
      name: 'hello',
      port: 8080,
      requestPath: '/hello',
      selfLink: 'https://www.googleapis.com/compute/beta/projects/my-project/global/httpsHealthChecks/hello',
      timeoutSec: 1,
      unhealthyThreshold: 1,
    },
    {
      account: 'my-gce-account',
      checkIntervalSec: 1,
      healthCheckType: 'HTTP',
      healthyThreshold: 1,
      kind: IGceHealthCheckKind.httpHealthCheck,
      name: 'ping',
      port: 8080,
      requestPath: '/ping',
      selfLink: 'https://www.googleapis.com/compute/beta/projects/my-project/global/httpHealthChecks/ping',
      timeoutSec: 1,
      unhealthyThreshold: 1,
    },
  ];
}
