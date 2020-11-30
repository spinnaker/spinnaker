import { REST } from 'core/api/ApiService';
import { IService } from 'core/domain';

export class ServicesReader {
  public static getServices(account: string, region: string): PromiseLike<IService[]> {
    return REST('/servicebroker')
      .path(account, 'services')
      .query({
        cloudProvider: 'cloudfoundry',
        region: region,
      })
      .get();
  }
}
