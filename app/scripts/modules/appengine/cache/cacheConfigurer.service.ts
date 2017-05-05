import {module} from 'angular';

import {ACCOUNT_SERVICE, AccountService, IAccount} from 'core/account/account.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {ILoadBalancer} from 'core/domain/index';

interface ICacheConfigEntry {
  initializers: Function[];
}

class AppengineCacheConfigurer {
  public credentials: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<IAccount[]> => this.accountService.listAccounts('appengine')]
  };

  public loadBalancers: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<ILoadBalancer[]> => this.loadBalancerReader.listLoadBalancers('appengine')]
  };

  constructor(private accountService: AccountService, private loadBalancerReader: any) { 'ngInject'; }
}

export const APPENGINE_CACHE_CONFIGURER = 'spinnaker.appengine.cacheConfigurer.service';

module(APPENGINE_CACHE_CONFIGURER, [
  ACCOUNT_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
]).service('appengineCacheConfigurer', AppengineCacheConfigurer);
