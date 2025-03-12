import { REST } from '../api/ApiService';

export class StorageAccountReader {
  public static getStorageAccounts(): PromiseLike<string[]> {
    return REST('/storage').get();
  }
}
