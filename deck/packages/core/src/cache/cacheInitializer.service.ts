import { cloneDeep, noop, uniq } from 'lodash';
import { Duration } from 'luxon';

import { AccountService } from '../account/AccountService';
import { CloudProviderRegistry } from '../cloudProvider/CloudProviderRegistry';
import type { ICacheConfig } from './deckCacheFactory';
import type { IInfrastructureCacheConfig } from './infrastructureCacheConfig';
import { INFRASTRUCTURE_CACHE_CONFIG } from './infrastructureCacheConfig';
import { InfrastructureCaches } from './infrastructureCaches';
import type { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import type { PromiseService } from '../utils/nativePromiseService';

interface IInitializers {
  [key: string]: any[];
  securityGroups: any[];
}

export class CacheInitializerService {
  private cacheConfig: IInfrastructureCacheConfig = cloneDeep<IInfrastructureCacheConfig>(INFRASTRUCTURE_CACHE_CONFIG);

  private initializers: IInitializers = {
    securityGroups: [() => this.securityGroupReader.getAllSecurityGroups()],
  };

  private setConfigDefaults(key: string, config: ICacheConfig) {
    config.version = config.version || 1;
    config.maxAge = config.maxAge || Duration.fromObject({ days: 2 }).as('milliseconds');
    config.initializers = config.initializers || this.initializers[key] || ([] as any[]);
    config.onReset = config.onReset || [noop];
  }

  private extendConfig(): Promise<void> {
    Object.keys(this.cacheConfig).forEach((key: string) => {
      this.setConfigDefaults(key, this.cacheConfig[key]);
    });

    return AccountService.listProviders().then((availableProviders: string[]) => {
      return CloudProviderRegistry.listRegisteredProviders().forEach((provider: string) => {
        if (!availableProviders.includes(provider)) {
          return;
        }

        if (this.providerServiceDelegate.hasDelegate(provider, 'cache.configurer')) {
          const providerConfig: any = this.providerServiceDelegate.getDelegate(provider, 'cache.configurer');
          Object.keys(providerConfig).forEach((key: string) => {
            this.setConfigDefaults(key, providerConfig[key]);
            if (!this.cacheConfig[key]) {
              this.cacheConfig[key] = providerConfig[key];
            }
            this.cacheConfig[key].initializers = uniq(
              this.cacheConfig[key].initializers.concat(providerConfig[key].initializers),
            );
            this.cacheConfig[key].onReset = uniq(this.cacheConfig[key].onReset.concat(providerConfig[key].onReset));
            this.cacheConfig[key].version = Math.max(this.cacheConfig[key].version, providerConfig[key].version);
            this.cacheConfig[key].maxAge = Math.min(this.cacheConfig[key].maxAge, providerConfig[key].maxAge);
          });
        }
      });
    });
  }

  private initializeCache(key: string): Promise<any[]> {
    InfrastructureCaches.createCache(key, this.cacheConfig[key]);
    if (this.cacheConfig[key].initializers) {
      const initializer: any = this.cacheConfig[key].initializers;
      const all: Array<Promise<any>> = [];
      initializer.forEach((method: Function) => {
        all.push(method());
      });

      return this.promiseService.all(all);
    } else {
      return this.promiseService.resolve(undefined);
    }
  }

  constructor(
    private promiseService: PromiseService,
    private securityGroupReader: SecurityGroupReader,
    private providerServiceDelegate: any,
  ) {}

  public initialize(): Promise<any[]> {
    return this.extendConfig().then(() => {
      const all: any[] = [];
      Object.keys(this.cacheConfig).forEach((key: string) => {
        all.push(this.initializeCache(key));
      });

      return this.promiseService.all(all);
    });
  }

  public refreshCache(key: string): Promise<any[]> {
    InfrastructureCaches.clearCache(key);
    return this.initializeCache(key);
  }

  public refreshCaches(): Promise<any[]> {
    const all: Array<Promise<any[]>> = [];
    Object.keys(this.cacheConfig).forEach((key: string) => {
      all.push(this.refreshCache(key));
    });

    return this.promiseService.all(all);
  }
}
