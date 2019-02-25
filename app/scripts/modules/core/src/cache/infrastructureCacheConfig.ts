import { Duration } from 'luxon';

export interface IMaxAgeConfig {
  maxAge: number;
}

export interface IVersionConfig {
  version: number;
}

export interface IInfrastructureCacheConfig {
  [key: string]: any;
  networks: IVersionConfig;
  vpcs: IVersionConfig;
  subnets: IVersionConfig;
  applications: IMaxAgeConfig;
  loadBalancers: IMaxAgeConfig;
  securityGroups: IVersionConfig;
  instanceTypes: IMaxAgeConfig & IVersionConfig;
  healthChecks: IVersionConfig;
  certificates: IVersionConfig;
  backendServices: IVersionConfig;
  addresses: IVersionConfig;
  credentials: any;
  buildMasters: any;
}

export const INFRASTRUCTURE_CACHE_CONFIG: IInfrastructureCacheConfig = {
  networks: {
    version: 2,
  },
  vpcs: {
    version: 2,
  },
  subnets: {
    version: 2,
  },
  applications: {
    maxAge: Duration.fromObject({ days: 30 }).as('milliseconds'), // it gets refreshed every time the user goes to the application list, anyway
  },
  loadBalancers: {
    maxAge: Duration.fromObject({ hours: 1 }).as('milliseconds'),
  },
  securityGroups: {
    version: 2, // increment to force refresh of cache on next page load - can be added to any cache
  },
  instanceTypes: {
    maxAge: Duration.fromObject({ days: 7 }).as('milliseconds'),
    version: 2,
  },
  healthChecks: {
    version: 2,
  },
  certificates: {
    version: 2,
  },
  backendServices: {
    version: 2,
  },
  addresses: {
    version: 2,
  },
  credentials: {},
  buildMasters: {},
};
