import angular from 'angular';
import { $injector } from 'ngimport';

/*
  When strict DI is enabled, AngularJS will throw an error at runtime whenever something
  is injected but not explicitly annotated.
  However, because this error is thrown at the time the injection occurs,
  bad code paths won't throw errors until somebody tries to use them.

  This file monkey-patches the angular.module function and eagerly throws errors at registration time for:

  - angular.module().config()
  - angular.module().run()
  - angular.module().animation()
  - angular.module().controller()
  - angular.module().factory()
  - angular.module().filter()
  - angular.module().provider()
  - angular.module().service()
  - angular.module().component()
  - angular.module().directive()

  This informs developers immediately if they try to create any angularjs code that isn't strictly annotated.
*/

function isAnnotatedArray(x: any) {
  return (
    Array.isArray(x) && x.slice(0, -1).every((v: any) => typeof v === 'string') && typeof x.slice(-1)[0] === 'function'
  );
}

function isAnnotatedFunction(x: any) {
  return typeof x === 'function' && Array.isArray(x.$inject) && x.$inject.every((e: any) => typeof e === 'string');
}

function isNoArg(x: any) {
  return typeof x === 'function' && x.length === 0;
}

function assertAnnotatedFunction(injectedFn: any, message: string) {
  if (!(isAnnotatedArray(injectedFn) || isAnnotatedFunction(injectedFn) || isNoArg(injectedFn))) {
    // eslint-disable-next-line
    [...arguments].forEach((arg, i) => console.log(i, arg)); // tslint:disable-line
    throw new Error(`StrictDI: ${message}: Expected this function to be explicitly annotated for AngularJS`);
  }
}

function invokeAnnotatedFunction(name: string, angularModuleFn: Function, argumentIdx: number) {
  return function () {
    assertAnnotatedFunction(arguments[argumentIdx], `angular.module().${name}()`);
    return angularModuleFn.apply(this, arguments);
  };
}

function validateDirectiveDefinitionObject(ddo: any) {
  const { controller } = ddo;
  if (
    controller &&
    !isAnnotatedArray(controller) &&
    !isAnnotatedFunction(controller) &&
    !isNoArg(controller) &&
    typeof controller !== 'string'
  ) {
    throw new Error(
      `StrictDI: angular.module().(directive|component).controller: Expected controller to be annotated for AngularJS`,
    );
  }
}

const angularJSModuleStrictDiHandler: ProxyHandler<any> = {
  get: function (angularModule, fnName, _receiver) {
    const angularModuleFn = angularModule[fnName];

    switch (fnName) {
      case 'config':
      case 'run':
        return invokeAnnotatedFunction(fnName, angularModuleFn, 0);
      case 'animation':
      case 'controller':
      case 'factory':
      case 'filter':
      case 'provider':
      case 'service':
        return invokeAnnotatedFunction(fnName, angularModuleFn, 1);
      case 'component':
        return function () {
          const [name, componentObject] = arguments;

          if (componentObject.controller && typeof componentObject.controller !== 'string') {
            assertAnnotatedFunction(
              componentObject.controller,
              `angular.module().component('${name}', componentObject.controller)`,
            );
          }

          return angularModuleFn.apply(this, arguments);
        };
      case 'directive':
        return function () {
          const [name, ddoFactory, ...rest] = arguments;

          assertAnnotatedFunction(ddoFactory, `angular.module().directive('${name}', ddoFactory)`);

          function wrappedFactory() {
            const ddo = $injector.invoke(ddoFactory);
            validateDirectiveDefinitionObject(ddo);
            return ddo;
          }

          return angularModuleFn.apply(this, [name, wrappedFactory, ...rest]);
        };
    }

    return angularModule[fnName];
  },
};

const allowlistedModules = ['ngMock'];
const realModule = angular.module;
(angular as any).module = function module() {
  const angularModule = realModule.apply(this, arguments);
  const isAllowlisted = allowlistedModules.includes(arguments[0]);
  return isAllowlisted ? angularModule : new Proxy(angularModule, angularJSModuleStrictDiHandler);
};
