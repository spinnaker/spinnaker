import { module } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';

export class StorageAccountService {
  constructor(private API: Api) { 'ngInject'; }

  public getStorageAccounts(): ng.IPromise<string[]> {
    return this.API.one('storage').get();
  }
}

export const STORAGE_ACCOUNT_SERVICE = 'spinnaker.core.storageAccount.service';
module(STORAGE_ACCOUNT_SERVICE, [API_SERVICE])
  .service('storageAccountService', StorageAccountService);
