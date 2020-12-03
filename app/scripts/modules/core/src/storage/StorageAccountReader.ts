import { REST } from 'core/api/ApiService';

export class StorageAccountReader {
  public static getStorageAccounts(): PromiseLike<string[]> {
    return REST().path('storage').get();
  }
}
