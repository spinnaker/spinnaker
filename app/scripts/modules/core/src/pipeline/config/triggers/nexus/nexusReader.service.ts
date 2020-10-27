

import { API } from 'core/api/ApiService';

export class NexusReaderService {
  public static getNexusNames(): PromiseLike<string[]> {
    return API.one('nexus').one('names').get();
  }
}
