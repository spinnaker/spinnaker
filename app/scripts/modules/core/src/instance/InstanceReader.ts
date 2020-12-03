import { API } from 'core/api/ApiService';
import { IInstance } from 'core/domain';

export interface IInstanceConsoleOutput {
  output: string | IInstanceMultiOutputLog[];
}

export interface IInstanceMultiOutputLog {
  name: string;
  output: string;
  formattedOutput?: string;
}

export class InstanceReader {
  public static getInstanceDetails(account: string, region: string, id: string): PromiseLike<IInstance> {
    return API.path('instances').path(account).path(region).path(id).get();
  }

  public static getConsoleOutput(
    account: string,
    region: string,
    id: string,
    cloudProvider: string,
  ): PromiseLike<IInstanceConsoleOutput> {
    return API.path('instances')
      .path(account)
      .path(region)
      .path(id, 'console')
      .query({ provider: cloudProvider })
      .get();
  }
}
