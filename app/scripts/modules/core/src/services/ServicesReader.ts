import { IPromise } from 'angular';
import { API } from 'core/api/ApiService';
import { IService } from 'core/domain';

export class ServicesReader {
  public static getServices(account: string): IPromise<IService[]> {
    return API.one('servicebroker')
      .one(account)
      .all('services')
      .withParams({
        cloudProvider: 'cloudfoundry',
      })
      .getList();
  }
}
