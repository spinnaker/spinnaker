import { IPromise, module } from 'angular';

import { API } from 'core/api/ApiService';
import { IFunctionSourceData } from 'core/domain';
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

  public loadFunctions(applicationName: string): IPromise<IFunctionSourceData[]> {
    return API.one('applications', applicationName)
      .all('functions')
      .getList()
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
  ): IPromise<IFunctionSourceData[]> {
    return API.all('functions')
      .withParams({ provider: cloudProvider, functionName: name, region: region, account: account })
      .get()
      .then((functions: IFunctionSourceData[]) => {
        functions = this.functionTransformer.normalizeFunctionSet(functions);
        return functions.map((fn) => this.normalizeFunction(fn));
      });
  }

  public listFunctions(cloudProvider: string): IPromise<IFunctionByAccount[]> {
    return API.all('functions').withParams({ provider: cloudProvider }).getList();
  }

  private normalizeFunction(functionDef: IFunctionSourceData): IFunctionSourceData {
    const fn = this.functionTransformer.normalizeFunction(functionDef);
    fn.cloudProvider = fn.cloudProvider || 'aws';
    return fn;
  }
}

export const FUNCTION_READ_SERVICE = 'spinnaker.core.function.read.service';

module(FUNCTION_READ_SERVICE, [CORE_FUNCTION_FUNCTION_TRANSFORMER]).service('functionReader', FunctionReader);
