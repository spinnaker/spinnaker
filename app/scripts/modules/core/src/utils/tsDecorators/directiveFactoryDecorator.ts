// Attribution: https://github.com/b091/ts-skeleton/blob/master/src/decorators/directive.ts:

export function DirectiveFactory(...values: string[]): any {

  return (target: Function) => {

    const directive: Function = (...argList: any[]): Object => {
      return ((classConstructor: Function, args: any[], ctor: any): Object => {
        ctor.prototype = classConstructor.prototype;
        const child: Object = new ctor;
        const result: Object = classConstructor.apply(child, args);
        return typeof result === 'object' ? result : child;
      })(target, argList, (): any => {
        return null;
      });
    };

    directive.$inject = values;
    return directive;
  };
}
