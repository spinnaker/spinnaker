import { module } from 'angular';

import { REST } from '../api/ApiService';
import { IFunctionSourceData } from '../domain';
import { CORE_FUNCTION_FUNCTION_TRANSFORMER, IFunctionTransformer } from './function.transformer';

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
  public static $inject = ['functionTransformer'];

  public constructor(private functionTransformer: IFunctionTransformer) {}

  public loadFunctions(applicationName: string): PromiseLike<IFunctionSourceData[]> {
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
  ): PromiseLike<IFunctionSourceData[]> {
    return REST('/functions')
      .query({ provider: cloudProvider, functionName: name, region: region, account: account })
      .get()
      .then((functions: IFunctionSourceData[]) => {
        functions = this.functionTransformer.normalizeFunctionSet(functions);
        return functions.map((fn) => this.normalizeFunction(fn));
      });
  }

  public listFunctions(cloudProvider: string): PromiseLike<IFunctionByAccount[]> {
    return REST('/functions').query({ provider: cloudProvider }).get();
  }

  private normalizeFunction(functionDef: IFunctionSourceData): IFunctionSourceData {
    const fn = this.functionTransformer.normalizeFunction(functionDef);
    fn.cloudProvider = fn.cloudProvider || 'aws';
    return fn;
  }
}

export const FUNCTION_READ_SERVICE = 'spinnaker.core.function.read.service';

module(FUNCTION_READ_SERVICE, [CORE_FUNCTION_FUNCTION_TRANSFORMER]).service('functionReader', FunctionReader);
