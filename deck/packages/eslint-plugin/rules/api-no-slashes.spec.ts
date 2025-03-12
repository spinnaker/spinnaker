import rule from './api-no-slashes';
import ruleTester from '../utils/ruleTester';
const errorMessage =
  `Do not include slashes in API.one() or API.all() calls because arguments to .one() and .all() get url encoded.` +
  `Instead, of API.one('foo/bar'), split into multiple arguments: API.one('foo', 'bar').`;

ruleTester.run('api-no-slashes', rule, {
  valid: [
    {
      code: `API.one('foo', 'bar');`,
    },
    {
      code: `const uri = "foo/bar/baz"; API.one(...uri.split('/'));`,
    },
  ],

  invalid: [
    {
      code: `API.one('foo/bad');`,
      output: `API.one('foo', 'bad');`,
      errors: [errorMessage],
    },
    // Fixes slashes in chained .one().all().one() calls
    {
      code: `API.one('ok').one('ok').all('ok').one('foo/bad');`,
      output: `API.one('ok').one('ok').all('ok').one('foo', 'bad');`,
      errors: [errorMessage],
    },
    // Fixes slashes in varargs arguments
    {
      code: `API.one('ok').one('ok', 'foo/bad');`,
      output: `API.one('ok').one('ok', 'foo', 'bad');`,
      errors: [errorMessage],
    },
    // Variables which are initialized to a string literal with a slash transform to split+spread
    {
      code: `let foo = "foo/bad"; API.one(foo);`,
      output: `let foo = "foo/bad"; API.one(...foo.split('/'));`,
      errors: [errorMessage],
    },
    // Expressions with literal slashes transform to split+spread
    {
      code: "API.one(`foo/${bad}`, 'bad');",
      output: "API.one(...(`foo/${bad}`).split('/'), 'bad');",
      errors: [errorMessage],
    },
    // Variables whose initializer contains a slash
    {
      code: 'let foo = `foo/${variable}`; API.one(foo);',
      output: "let foo = `foo/${variable}`; API.one(...foo.split('/'));",
      errors: [errorMessage],
    },
    // Variables whose initializer contains a slash (anywhere)
    {
      code: 'let foo = "foo/bad" + variable; API.one(foo);',
      output: `let foo = "foo/bad" + variable; API.one(...foo.split('/'));`,
      errors: [errorMessage],
    },
    // Variables whose initializer contains a slash (mix of everything, still can be detected)
    {
      code: 'let foo = variable + `foo/${expr}` + "/bad/" + variable; API.one(foo);',
      output: 'let foo = variable + `foo/${expr}` + "/bad/" + variable; API.one(...foo.split(\'/\'));',
      errors: [errorMessage],
    },
    // Multiple errors, mix of styles
    {
      code: `const foo = "foo/bad"; API.all('api').one(foo).one('ok', 'bar/baz');`,
      output: `const foo = "foo/bad"; API.all('api').one(...foo.split('/')).one('ok', 'bar', 'baz');`,
      errors: [errorMessage, errorMessage], // two errors
    },
    // Detectable but unfixable
    {
      code: 'API.one(`foo/${template}`);',
      output: "API.one(...(`foo/${template}`).split('/'));",
      errors: [errorMessage], // two errors
    },
    // Detectable but unfixable
    {
      code: 'API.one("foo/" + expressions);',
      output: `API.one(...("foo/" + expressions).split('/'));`,
      errors: [errorMessage], // two errors
    },
  ],
});
