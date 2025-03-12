import rule from './prefer-promise-like';
import ruleTester from '../utils/ruleTester';
const errorMessage = `Prefer using PromiseLike type instead of AngularJS IPromise.`;
const unusedImportErrorMessage = `Unused IPromise import`;

ruleTester.run('prefer-promise-like', rule, {
  valid: [
    {
      code: `const foo: PromiseLike<any> = API.one('foo', 'bar').get();`,
    },
  ],

  invalid: [
    // IPromise in variable
    {
      code: `const foo: IPromise<any> = API.one('foo', 'bar').get();`,
      output: `const foo: PromiseLike<any> = API.one('foo', 'bar').get();`,
      errors: [errorMessage],
    },
    // IPromise in function arg
    {
      code: `function foo(promise: IPromise<any>) {}`,
      output: `function foo(promise: PromiseLike<any>) {}`,
      errors: [errorMessage],
    },
    // IPromise in class method return
    {
      code: `class Foo { foo(): IPromise<any> {} }`,
      output: `class Foo { foo(): PromiseLike<any> {} }`,
      errors: [errorMessage],
    },
    // ng.IPromise in variable
    {
      code: `const foo: ng.IPromise<any> = API.one('foo', 'bar').get();`,
      output: `const foo: PromiseLike<any> = API.one('foo', 'bar').get();`,
      errors: [errorMessage],
    },
    // ng.IPromise in function arg
    {
      code: `function foo(promise: ng.IPromise<any>) {}`,
      output: `function foo(promise: PromiseLike<any>) {}`,
      errors: [errorMessage],
    },
    // ng.IPromise in class method return
    {
      code: `class Foo { foo(): ng.IPromise<any> {} }`,
      output: `class Foo { foo(): PromiseLike<any> {} }`,
      errors: [errorMessage],
    },
    // Unused IPromise import
    {
      code: `import { IPromise } from 'angular';`,
      output: ``,
      errors: [unusedImportErrorMessage],
    },
    // Unused IPromise import 2
    {
      code: `import { module, IPromise } from 'angular';`,
      output: `import { module } from 'angular';`,
      errors: [unusedImportErrorMessage],
    },
    // Unused IPromise import 3
    {
      code: `import { IPromise, module } from 'angular';`,
      output: `import { module } from 'angular';`,
      errors: [unusedImportErrorMessage],
    },
    // Unused IPromise import 4
    {
      code: `import { QService, IPromise, module } from 'angular';`,
      output: `import { QService, module } from 'angular';`,
      errors: [unusedImportErrorMessage],
    },
  ],
});
