import { truncate } from 'lodash';

import { parseSpelExpressions } from './spel2js.templateParser';

export interface ISpelError {
  message: string;
  context: string;
  contextTruncated: string;
}

export interface IExpressionChange {
  value: string;
  spelError: ISpelError;
  spelPreview: string;
}

export const evaluateExpression = (context: object, value: string): IExpressionChange => {
  if (!value) {
    return { value, spelError: null, spelPreview: '' };
  }

  const stringify = (obj: any): string => {
    return obj === null ? 'null' : obj === undefined ? 'undefined' : JSON.stringify(obj, null, 2);
  };

  try {
    const exprs = parseSpelExpressions(value);
    const results = exprs.map((expr) => expr.eval(context));
    return { value, spelError: null, spelPreview: results.join('') };
  } catch (err) {
    const spelError: ISpelError = {
      message: null,
      context: null,
      contextTruncated: null,
    };

    if (err.name && err.message) {
      if (err.name === 'NullPointerException' && err.state && err.state.activeContext) {
        spelError.context = stringify(err.state.activeContext.peek());
        spelError.contextTruncated = truncate(spelError.context, { length: 200 });
      }
      spelError.message = `${err.name}: ${err.message}`;
    } else {
      try {
        spelError.message = JSON.stringify(err);
      } catch (ignored) {
        spelError.message = err.toString();
      }
    }

    return { value, spelError, spelPreview: null };
  }
};
