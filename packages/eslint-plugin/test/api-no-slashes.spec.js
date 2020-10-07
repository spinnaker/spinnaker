'use strict';

const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/api-no-slashes');
const errorMessage =
  `Do not include slashes in API.one() or API.all() calls because arguments to .one() and .all() get url encoded.` +
  `Instead, of API.one('foo/bar'), split into multiple arguments: API.one('foo', 'bar').`;

ruleTester.run('api-no-slashes', rule, {
  valid: [
    {
      code: `API.one('foo', 'bar');`,
    },
  ],

  invalid: [
    {
      code: `API.one('foo/bad');`,
      errors: [errorMessage],
      output: `API.one('foo', 'bad');`,
    },
    {
      code: `API.one('ok').one('ok').all('ok').one('foo/bad');`,
      errors: [errorMessage],
      output: `API.one('ok').one('ok').all('ok').one('foo', 'bad');`,
    },
    {
      code: `API.one('ok').one('ok', 'foo/bad');`,
      output: `API.one('ok').one('ok', 'foo', 'bad');`,
      errors: [errorMessage],
    },
    // Variables which are initialized to a string literal with a slash
    {
      code: `let foo = "foo/bad"; API.one(foo);`,
      errors: [errorMessage],
      output: `let foo = "foo/bad"; API.one(...foo.split('/'));`,
    },
    // Mix of everything
    {
      code: `const foo = "foo/bad"; API.all('api').one(foo).one('ok', 'bar/baz');`,
      errors: [errorMessage, errorMessage], // two errors
      output: `const foo = "foo/bad"; API.all('api').one(...foo.split('/')).one('ok', 'bar', 'baz');`,
    },
  ],
});
