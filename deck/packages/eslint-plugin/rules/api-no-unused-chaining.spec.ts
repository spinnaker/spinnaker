import rule from '../rules/api-no-unused-chaining';
import ruleTester from '../utils/ruleTester';
const errorMessage = (text) => `Unused API.xyz() method chaining no longer works. Re-assign the result of: ${text}`;

ruleTester.run('api-no-slashes', rule, {
  valid: [
    { code: `const fooBar = API.one('foo', 'bar');` },
    { code: `let fooBar = API.one('foo', 'bar'); fooBar = fooBar.useCache()` },
    { code: `const promise = API.one('foo', 'bar').all('baz').get();` },
  ],

  invalid: [
    {
      code: `API.one('foo/bad');`,
      errors: [errorMessage(`API.one('foo/bad');`)],
    },
    {
      code: `var foo = API.one('foo/bad'); foo.useCache(true);`,
      errors: [errorMessage(`foo.useCache(true);`)],
    },
    {
      code: `var foo = API.one('foo/bad'); foo.withParams({});`,
      errors: [errorMessage(`foo.withParams({});`)],
    },
  ],
});
