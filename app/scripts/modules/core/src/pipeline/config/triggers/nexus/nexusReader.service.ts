import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';

export class NexusReaderService {
  public static getNexusNames(): IPromise<string[]> {
    return API.one('nexus').one('names').get();
  }
}
