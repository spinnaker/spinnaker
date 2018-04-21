import { module } from 'angular';
import { API } from 'core/api/ApiService';
import { IInstance } from 'core/domain';

export interface IInstanceConsoleOutput {
  output: string;
}

export class InstanceReader {
  public getInstanceDetails(account: string, region: string, id: string): ng.IPromise<IInstance> {
    return API.one('instances')
      .one(account)
      .one(region)
      .one(id)
      .get();
  }

  public getConsoleOutput(
    account: string,
    region: string,
    id: string,
    cloudProvider: string,
  ): ng.IPromise<IInstanceConsoleOutput> {
    return API.one('instances')
      .all(account)
      .all(region)
      .one(id, 'console')
      .withParams({ provider: cloudProvider })
      .get();
  }
}

export const INSTANCE_READ_SERVICE = 'spinnaker.core.instance.read.service';
module(INSTANCE_READ_SERVICE, []).service('instanceReader', InstanceReader);
