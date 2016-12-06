import {module} from 'angular';

import {ACCOUNT_SERVICE, AccountService, IAccount} from 'core/account/account.service';
import {LoadBalancer} from 'core/domain/index';

interface ICacheConfigEntry {
  initializers: Function[];
}

class AppengineCacheConfigurer {
  public credentials: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<IAccount[]> => this.accountService.listAccounts('appengine')]
  };

  public loadBalancers: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<LoadBalancer[]> => this.loadBalancerReader.listLoadBalancers('appengine')]
  };

  static get $inject() { return ['$q', 'accountService', 'loadBalancerReader']; }

  constructor(private $q: ng.IQService, private accountService: AccountService, private loadBalancerReader: any) { }
}

export const APPENGINE_CACHE_CONFIGURER = 'spinnaker.appengine.cacheConfigurer.service';

module(APPENGINE_CACHE_CONFIGURER, [
  ACCOUNT_SERVICE,
  require('core/loadBalancer/loadBalancer.read.service'),
]).service('appengineCacheConfigurer', AppengineCacheConfigurer);
