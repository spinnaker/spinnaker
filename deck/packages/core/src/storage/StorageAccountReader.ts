import { REST } from '../api/ApiService';

export class StorageAccountReader {
  public static getStorageAccounts(): Promise<string[]> {
    return REST('/storage').get();
  }
}
