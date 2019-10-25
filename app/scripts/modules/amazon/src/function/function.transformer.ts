import { IAmazonFunction } from 'amazon/domain';

export class AwsFunctionTransformer {
  public normalizeFunction(functionDef: IAmazonFunction): IAmazonFunction {
    return functionDef;
  }
}
