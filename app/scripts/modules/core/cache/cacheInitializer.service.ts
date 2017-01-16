import * as moment from 'moment';
import {cloneDeep, uniq} from 'lodash';
import {module, noop} from 'angular';

import {
  APPLICATION_READ_SERVICE, ApplicationReader} from 'core/application/service/application.read.service';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {INFRASTRUCTURE_CACHE_CONFIG, IInfrastructureCacheConfig} from './infrastructureCacheConfig';
import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from './infrastructureCaches.service';
import {ICacheConfig} from './deckCache.service';
import {SECURITY_GROUP_READER, SecurityGroupReader} from 'core/securityGroup/securityGroupReader.service';

interface IInitializers {
  [key: string]: any[];
  credentials: any[];
  securityGroups: any[];
  applications: any[];
  buildMasters: any[];
}

export class CacheInitializerService {

  private cacheConfig: IInfrastructureCacheConfig = cloneDeep<IInfrastructureCacheConfig>(INFRASTRUCTURE_CACHE_CONFIG);

  private initializers: IInitializers = {
    credentials: [() => this.accountService.listAccounts()],
    securityGroups: [() => this.securityGroupReader.getAllSecurityGroups()],
    applications: [() => this.applicationReader.listApplications()],
    buildMasters: [() => this.igorService.listMasters()]
  };

  private setConfigDefaults(key: string, config: ICacheConfig) {
    config.version = config.version || 1;
    config.maxAge = config.maxAge || moment.duration(2, 'days').asMilliseconds();
    config.initializers = config.initializers || this.initializers[key] || <any[]>[];
    config.onReset = config.onReset || [noop];
  }

  private extendConfig(): ng.IPromise<void> {
    Object.keys(this.cacheConfig).forEach((key: string) => {
      this.setConfigDefaults(key, this.cacheConfig[key]);
    });

    return this.accountService.listProviders().then((availableProviders: string[]) => {
      return this.cloudProviderRegistry.listRegisteredProviders().forEach((provider: string) => {
        if (!availableProviders.includes(provider)) {
          return;
        }

        if (this.serviceDelegate.hasDelegate(provider, 'cache.configurer')) {
          const providerConfig: any = this.serviceDelegate.getDelegate(provider, 'cache.configurer');
          Object.keys(providerConfig).forEach((key: string) => {
            this.setConfigDefaults(key, providerConfig[key]);
            if (!this.cacheConfig[key]) {
              this.cacheConfig[key] = providerConfig[key];
            }
            this.cacheConfig[key].initializers =
              uniq((this.cacheConfig[key].initializers).concat(providerConfig[key].initializers));
            this.cacheConfig[key].onReset =
              uniq((this.cacheConfig[key].onReset).concat(providerConfig[key].onReset));
            this.cacheConfig[key].version =
              Math.max(this.cacheConfig[key].version, providerConfig[key].version);
            this.cacheConfig[key].maxAge =
              Math.min(this.cacheConfig[key].maxAge, providerConfig[key].maxAge);
          });
        }
      });
    });
  }

  private initializeCache(key: string): ng.IPromise<any[]> {

    this.infrastructureCaches.createCache(key, this.cacheConfig[key]);
    if (this.cacheConfig[key].initializers) {
      const initializer: any = this.cacheConfig[key].initializers;
      const all: ng.IPromise<any>[] = [];
      initializer.forEach((method: Function) => {
        all.push(method());
      });

      return this.$q.all(all);
    } else {
      return this.$q.resolve(undefined);
    }
  }

  constructor(private $q: ng.IQService,
              private applicationReader: ApplicationReader,
              private infrastructureCaches: InfrastructureCacheService,
              private accountService: AccountService,
              private securityGroupReader: SecurityGroupReader,
              private cloudProviderRegistry: CloudProviderRegistry,
              private igorService: any,
              private serviceDelegate: any) {}

  public initialize(): ng.IPromise<any[]> {

    return this.extendConfig().then(() => {
      const all: any[] = [];
      Object.keys(this.cacheConfig).forEach((key: string) => {
        all.push(this.initializeCache(key));
      });

      return this.$q.all(all);
    });
  }

  public refreshCache(key: string): ng.IPromise<any[]> {
    this.infrastructureCaches.clearCache(key);
    return this.initializeCache(key);
  }

  public refreshCaches(): ng.IPromise<any[]> {

    const all: ng.IPromise<any[]>[] = [];
    Object.keys(this.cacheConfig).forEach((key: string) => {
      all.push(this.refreshCache(key));
    });

    return this.$q.all(all);
  }
}

export const CACHE_INITIALIZER_SERVICE = 'spinnaker.core.cache.initializer';
module(CACHE_INITIALIZER_SERVICE, [
  ACCOUNT_SERVICE,
  SECURITY_GROUP_READER,
  APPLICATION_READ_SERVICE,
  require('../ci/jenkins/igor.service.js'),
  INFRASTRUCTURE_CACHE_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
])
  .service('cacheInitializer', CacheInitializerService);
