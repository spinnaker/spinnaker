import {module} from 'angular';

import {ACCOUNT_SERVICE, AccountService, IAccount} from 'core/account/account.service';

interface ICacheConfigEntry {
  initializers: Function[];
}

class AppengineCacheConfigurer {
  public credentials: ICacheConfigEntry = {
    initializers: [(): ng.IPromise<IAccount[]> => this.accountService.listAccounts('appengine') ]
  };

  static get $inject() { return ['$q', 'accountService']; }

  constructor(private $q: ng.IQService, private accountService: AccountService) { }
}

export const APPENGINE_CACHE_CONFIGURER = 'spinnaker.appengine.cacheConfigurer.service';

module(APPENGINE_CACHE_CONFIGURER, [
  ACCOUNT_SERVICE,
]).service('appengineCacheConfigurer', AppengineCacheConfigurer);
