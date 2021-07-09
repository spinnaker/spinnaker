'use strict';

import { module } from 'angular';
import { chain, flow } from 'lodash';

import { PROVIDER_SERVICE_DELEGATE } from '../cloudProvider/providerService.delegate';
import { IFunctionSourceData } from '../index';

export const CORE_FUNCTION_FUNCTION_TRANSFORMER = 'spinnaker.core.function.transformer';
export const name = CORE_FUNCTION_FUNCTION_TRANSFORMER; // for backwards compatibility

export interface IFunctionTransformer {
  normalizeFunction: (functionDef: IFunctionSourceData) => IFunctionSourceData;
  normalizeFunctionSet: (functions: IFunctionSourceData[]) => IFunctionSourceData[];
}

module(CORE_FUNCTION_FUNCTION_TRANSFORMER, [PROVIDER_SERVICE_DELEGATE]).factory('functionTransformer', [
  'providerServiceDelegate',
  function (providerServiceDelegate: any): IFunctionTransformer {
    function normalizeFunction(functionDef: IFunctionSourceData): IFunctionSourceData {
      return providerServiceDelegate
        .getDelegate(functionDef.provider ? functionDef.provider : 'aws', 'function.transformer')
        .normalizeFunction(functionDef);
    }

    function normalizeFunctionSet(functions: IFunctionSourceData[]): IFunctionSourceData[] {
      const setNormalizers: any = chain(functions)
        .filter((fn) =>
          providerServiceDelegate.hasDelegate(fn.provider ? fn.provider : 'aws', 'function.setTransformer'),
        )
        .compact()
        .map((fn) => providerServiceDelegate.getDelegate(fn.provider, 'function.setTransformer').normalizeFunctionSet)
        .uniq()
        .value();

      if (setNormalizers.length) {
        return flow(setNormalizers)(functions);
      } else {
        return functions;
      }
    }

    return {
      normalizeFunction: normalizeFunction,
      normalizeFunctionSet: normalizeFunctionSet,
    };
  },
]);
