import { module } from 'angular';

import { API } from 'core/api/ApiService';

export class StorageAccountService {
  public getStorageAccounts(): ng.IPromise<string[]> {
    return API.one('storage').get();
  }
}

export const STORAGE_ACCOUNT_SERVICE = 'spinnaker.core.storageAccount.service';
module(STORAGE_ACCOUNT_SERVICE, []).service('storageAccountService', StorageAccountService);
