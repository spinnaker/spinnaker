import { REST } from '../api/ApiService';
import type { IFunctionSourceData } from '../domain';
import type { IFunctionTransformer } from './function.transformer';

export interface IFunctionByAccount {
  name: string;
  accounts: Array<{
    name: string;
    regions: Array<{
      name: string;
      functions: IFunctionSourceData[];
    }>;
  }>;
}

export class FunctionReader {
  public constructor(private functionTransformer: IFunctionTransformer) {}

  public loadFunctions(applicationName: string): Promise<IFunctionSourceData[]> {
    return REST('/applications')
      .path(applicationName, 'functions')
      .get()
      .then((functions: IFunctionSourceData[]) => {
        functions = this.functionTransformer.normalizeFunctionSet(functions);
        return functions.map((fn) => this.normalizeFunction(fn));
      });
  }

  public getFunctionDetails(
    cloudProvider: string,
    account: string,
    region: string,
    name: string,
  ): Promise<IFunctionSourceData[]> {
    return REST('/functions')
      .query({ provider: cloudProvider, functionName: name, region: region, account: account })
      .get()
      .then((functions: IFunctionSourceData[]) => {
        functions = this.functionTransformer.normalizeFunctionSet(functions);
        return functions.map((fn) => this.normalizeFunction(fn));
      });
  }

  public listFunctions(cloudProvider: string): Promise<IFunctionByAccount[]> {
    return REST('/functions').query({ provider: cloudProvider }).get();
  }

  private normalizeFunction(functionDef: IFunctionSourceData): IFunctionSourceData {
    const fn = this.functionTransformer.normalizeFunction(functionDef);
    fn.cloudProvider = fn.cloudProvider || 'aws';
    return fn;
  }
}
