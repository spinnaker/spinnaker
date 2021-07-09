import { REST } from '../../../../api/ApiService';

export class NexusReaderService {
  public static getNexusNames(): PromiseLike<string[]> {
    return REST('/nexus/names').get();
  }
}
