import { REST } from '../../../../api/ApiService';

export class NexusReaderService {
  public static getNexusNames(): Promise<string[]> {
    return REST('/nexus/names').get();
  }
}
