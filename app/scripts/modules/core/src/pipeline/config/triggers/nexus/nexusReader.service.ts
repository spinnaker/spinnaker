import { REST } from 'core/api/ApiService';

export class NexusReaderService {
  public static getNexusNames(): PromiseLike<string[]> {
    return REST().path('nexus', 'names').get();
  }
}
