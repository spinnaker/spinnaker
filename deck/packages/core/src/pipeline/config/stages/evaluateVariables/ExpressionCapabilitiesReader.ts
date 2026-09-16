import { REST } from '../../../../api';

export interface IExpressionCapabilities {
  functions: unknown[];
  spelEvaluators: unknown[];
  dashedIdentifiersEnabled: boolean;
}

export class ExpressionCapabilitiesReader {
  public static getExpressionCapabilities(): Promise<IExpressionCapabilities> {
    return REST('/capabilities/expressions').useCache(true).get();
  }
}
