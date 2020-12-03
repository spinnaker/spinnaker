import { API } from 'core/api/ApiService';

export class NexusReaderService {
  public static getNexusNames(): PromiseLike<string[]> {
    return API.path('nexus').path('names').get();
  }
}
