import { REST } from '../api/ApiService';
import { IInstance } from '../domain';

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
    return REST('/instances').path(account, region, id).get();
  }

  public static getConsoleOutput(
    account: string,
    region: string,
    id: string,
    cloudProvider: string,
  ): PromiseLike<IInstanceConsoleOutput> {
    return REST('/instances').path(account, region, id, 'console').query({ provider: cloudProvider }).get();
  }
}
