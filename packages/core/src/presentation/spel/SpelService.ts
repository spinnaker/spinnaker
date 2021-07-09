import { isString } from 'lodash';

import { REST } from '../../api';

import { parseSpelExpressions } from '../forms/inputs/expression';

interface IEvaluationException {
  description: string;
  exceptionType: string;
  level: string;
  timestamp: number;
}

interface IServerSideEvaluatedExpression {
  result: any;
  detail?: {
    [expression: string]: IEvaluationException[];
  };
}

export class SpelService {
  /** Evaluates a spel expression on the server against a previous executionId and stageId */
  public static evaluateExpression(expression: string, executionId: string, stageId: string) {
    return REST('/pipelines')
      .path(executionId, stageId, 'evaluateExpression')
      .query({ expression })
      .get()
      .then((result: IServerSideEvaluatedExpression) => {
        const errors = result.detail || {};
        const firstErrorKey = Object.keys(errors)[0];
        const firstError = errors[firstErrorKey];

        if (firstError && firstError.length) {
          throw new Error(`${firstError[0].exceptionType}: ${firstError[0].description}`);
        }

        return result.result;
      });
  }

  /**
   * Parses an expression string (client-side)
   *
   * Throws an error if the expression is syntactically invalidat
   *
   * @param expression a SpEL expression templated string.
   *        e.g.: "This is a ${trigger.type} trigger"
   */
  public static parseExpressionString(expression: string): void {
    parseSpelExpressions(expression);
  }

  /**
   * Determines if a string is a SpEL expression
   *
   * @param value The value to check for SpEL
   */
  public static includesSpel(value: string): boolean {
    return isString(value) && value.includes('${');
  }
}
