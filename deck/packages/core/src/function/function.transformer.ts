'use strict';

import type { IFunctionSourceData } from '../index';

export interface IFunctionTransformer {
  normalizeFunction: (functionDef: IFunctionSourceData) => IFunctionSourceData;
  normalizeFunctionSet: (functions: IFunctionSourceData[]) => IFunctionSourceData[];
}
