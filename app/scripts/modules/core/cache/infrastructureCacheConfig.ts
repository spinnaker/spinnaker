import * as moment from 'moment';

interface IMaxAgeConfig {
  maxAge: number;
}

interface IVersionConfig {
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
  httpHealthChecks: IVersionConfig;
  certificates: IVersionConfig;
  backendServices: IVersionConfig;
  addresses: IVersionConfig;
  credentials: any;
  buildMasters: any;
}

export const INFRASTRUCTURE_CACHE_CONFIG: IInfrastructureCacheConfig = {
  networks: {
    version: 2
  },
  vpcs: {
    version: 2
  },
  subnets: {
    version: 2
  },
  applications: {
    maxAge: moment.duration(30, 'days').asMilliseconds() // it gets refreshed every time the user goes to the application list, anyway
  },
  loadBalancers: {
    maxAge: moment.duration(1, 'hour').asMilliseconds()
  },
  securityGroups: {
    version: 2 // increment to force refresh of cache on next page load - can be added to any cache
  },
  instanceTypes: {
    maxAge: moment.duration(7, 'days').asMilliseconds(),
    version: 2
  },
  healthChecks: {
    version: 2
  },
  httpHealthChecks: {
    version: 2
  },
  certificates: {
    version: 2
  },
  backendServices: {
    version: 2
  },
  addresses: {
    version: 2
  },
  credentials: {},
  buildMasters: {}
};
