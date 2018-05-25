import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';

export class StorageAccountReader {
  public static getStorageAccounts(): IPromise<string[]> {
    return API.one('storage').get();
  }
}
