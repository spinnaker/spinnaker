declare module 'spel2js' {
  export interface SpelExpression {
    eval(context: object = {}, locals: object = {}): any;
    _compiledExpression: any;
  }

  export const SpelExpressionEvaluator: {
    compile(expression: string): SpelExpression;
    eval(expression: string, context: object, locals: object): any;
  };

  export interface SpelAuthContext {
    authentication: object;
    principal: object;
    hasRole(role: string): boolean;
    hasPermission(...any): boolean;
  }

  export const StandardContext: {
    create(authentication: object, principal: object): SpelAuthContext;
  };

  export const TemplateParser: {
    parse(template: string): SpelExpression[];
  };
}
