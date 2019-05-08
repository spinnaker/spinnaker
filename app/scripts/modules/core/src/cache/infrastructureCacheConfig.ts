export interface IMaxAgeConfig {
  maxAge: number;
}

export interface IVersionConfig {
  version: number;
}

export interface IInfrastructureCacheConfig {
  [key: string]: any;
  securityGroups: IVersionConfig;
  healthChecks: IVersionConfig;
  certificates: IVersionConfig;
  backendServices: IVersionConfig;
  addresses: IVersionConfig;
}

export const INFRASTRUCTURE_CACHE_CONFIG: IInfrastructureCacheConfig = {
  securityGroups: {
    version: 3, // increment to force refresh of cache on next page load - can be added to any cache
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
};
