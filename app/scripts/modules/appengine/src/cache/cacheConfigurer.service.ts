import { module } from 'angular';

import { AccountService, IAccount, ILoadBalancer, LOAD_BALANCER_READ_SERVICE } from '@spinnaker/core';

interface ICacheConfigEntry {
  initializers: Function[];
}

class AppengineCacheConfigurer {
  public credentials: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<IAccount[]> => AccountService.listAccounts('appengine')],
  };

  public loadBalancers: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<ILoadBalancer[]> => this.loadBalancerReader.listLoadBalancers('appengine')],
  };

  public static $inject = ['loadBalancerReader'];
  constructor(private loadBalancerReader: any) {}
}

export const APPENGINE_CACHE_CONFIGURER = 'spinnaker.appengine.cacheConfigurer.service';

module(APPENGINE_CACHE_CONFIGURER, [LOAD_BALANCER_READ_SERVICE]).service(
  'appengineCacheConfigurer',
  AppengineCacheConfigurer,
);
