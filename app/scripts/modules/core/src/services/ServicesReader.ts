import { API } from 'core/api/ApiService';
import { IService } from 'core/domain';

export class ServicesReader {
  public static getServices(account: string, region: string): PromiseLike<IService[]> {
    return API.path('servicebroker')
      .path(account)
      .path('services')
      .query({
        cloudProvider: 'cloudfoundry',
        region: region,
      })
      .get();
  }
}
