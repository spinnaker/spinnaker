import { IPromise } from 'angular';
import { API } from 'core/api/ApiService';
import { IInstance } from 'core/domain';

export interface IInstanceConsoleOutput {
  output: string | IInstanceMultiOutputLog[];
}

export interface IInstanceMultiOutputLog {
  name: string;
  output: string;
}

export class InstanceReader {
  public static getInstanceDetails(account: string, region: string, id: string): IPromise<IInstance> {
    return API.one('instances')
      .one(account)
      .one(region)
      .one(id)
      .get();
  }

  public static getConsoleOutput(
    account: string,
    region: string,
    id: string,
    cloudProvider: string,
  ): IPromise<IInstanceConsoleOutput> {
    return API.one('instances')
      .all(account)
      .all(region)
      .one(id, 'console')
      .withParams({ provider: cloudProvider })
      .get();
  }
}
