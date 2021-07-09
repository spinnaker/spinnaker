import { IQService, module, noop } from 'angular';
import { cloneDeep, uniq } from 'lodash';
import { Duration } from 'luxon';

import { AccountService } from '../account/AccountService';
import { CloudProviderRegistry } from '../cloudProvider';
import { ICacheConfig } from './deckCacheFactory';
import { IInfrastructureCacheConfig, INFRASTRUCTURE_CACHE_CONFIG } from './infrastructureCacheConfig';
import { InfrastructureCaches } from './infrastructureCaches';
import { SECURITY_GROUP_READER, SecurityGroupReader } from '../securityGroup/securityGroupReader.service';

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

  private extendConfig(): PromiseLike<void> {
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

  private initializeCache(key: string): PromiseLike<any[]> {
    InfrastructureCaches.createCache(key, this.cacheConfig[key]);
    if (this.cacheConfig[key].initializers) {
      const initializer: any = this.cacheConfig[key].initializers;
      const all: Array<PromiseLike<any>> = [];
      initializer.forEach((method: Function) => {
        all.push(method());
      });

      return this.$q.all(all);
    } else {
      return this.$q.resolve(undefined);
    }
  }

  public static $inject = ['$q', 'securityGroupReader', 'providerServiceDelegate'];
  constructor(
    private $q: IQService,
    private securityGroupReader: SecurityGroupReader,
    private providerServiceDelegate: any,
  ) {}

  public initialize(): PromiseLike<any[]> {
    return this.extendConfig().then(() => {
      const all: any[] = [];
      Object.keys(this.cacheConfig).forEach((key: string) => {
        all.push(this.initializeCache(key));
      });

      return this.$q.all(all);
    });
  }

  public refreshCache(key: string): PromiseLike<any[]> {
    InfrastructureCaches.clearCache(key);
    return this.initializeCache(key);
  }

  public refreshCaches(): PromiseLike<any[]> {
    const all: Array<PromiseLike<any[]>> = [];
    Object.keys(this.cacheConfig).forEach((key: string) => {
      all.push(this.refreshCache(key));
    });

    return this.$q.all(all);
  }
}

export const CACHE_INITIALIZER_SERVICE = 'spinnaker.core.cache.initializer';
module(CACHE_INITIALIZER_SERVICE, [SECURITY_GROUP_READER]).service('cacheInitializer', CacheInitializerService);
