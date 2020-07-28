import { ICacheConfig } from './deckCacheFactory';

export interface IInfrastructureCacheConfig {
  [key: string]: any;
  securityGroups: ICacheConfig;
  healthChecks: ICacheConfig;
  certificates: ICacheConfig;
  backendServices: ICacheConfig;
  addresses: ICacheConfig;
}

export const INFRASTRUCTURE_CACHE_CONFIG: IInfrastructureCacheConfig = {
  securityGroups: {
    version: 4, // increment to force refresh of cache on next page load - can be added to any cache
    storageMode: 'memory',
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
