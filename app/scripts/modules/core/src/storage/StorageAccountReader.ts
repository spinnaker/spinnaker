

import { API } from 'core/api/ApiService';

export class StorageAccountReader {
  public static getStorageAccounts(): PromiseLike<string[]> {
    return API.one('storage').get();
  }
}
