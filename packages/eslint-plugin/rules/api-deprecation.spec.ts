import rule from './api-deprecation';
import ruleTester from '../utils/ruleTester';

ruleTester.run('api-deprecation', rule, {
  valid: [
    {
      code: `REST('/path/to/endpoint').path(id).get();`,
    },
  ],

  invalid: [
    // Simple renames: .one(), .all(), .getList(), .withParams(), .remove()
    {
      code: `API.one('foo');`,
      output: `API.path('foo');`,
      errors: ['API.one() is deprecated.  Migrate from one() to path()'],
    },
    {
      code: `API.all('foo');`,
      output: `API.path('foo');`,
      errors: ['API.all() is deprecated.  Migrate from all() to path()'],
    },
    {
      code: `API.path('foo').getList();`,
      output: `API.path('foo').get();`,
      errors: ['API.getList() is deprecated.  Migrate from getList() to get()'],
    },
    {
      code: `API.withParams('foo');`,
      output: `API.query('foo');`,
      errors: ['API.withParams() is deprecated.  Migrate from withParams() to query()'],
    },
    {
      code: `API.remove('foo');`,
      output: `API.delete('foo');`,
      errors: ['API.remove() is deprecated.  Migrate from remove() to delete()'],
    },
    // .data(obj).post() -> .post(obj)
    {
      code: `API.path('foo').data({ key: 'val' }).post();`,
      output: `API.path('foo').post({ key: 'val' });`,
      errors: ['API.data() is deprecated.  Migrate from .data({}) to .put({}) or .post({})'],
    },
    // .data(obj).put() -> .put(obj)
    {
      code: `API.path('foo').data({ key: 'val' }).put();`,
      output: `API.path('foo').put({ key: 'val' });`,
      errors: ['API.data() is deprecated.  Migrate from .data({}) to .put({}) or .post({})'],
    },
    // .get() doesn't have queryparams args
    {
      code: `API.path('foo').get(queryParams);`,
      output: `API.path('foo').query(queryParams).get();`,
      errors: [
        'Passing parameters to API.get() is deprecated.  Migrate from .get(queryparams) to .query(queryparams).get()',
      ],
    },
    // .delete() doesn't have queryparams args
    {
      code: `API.path('foo').delete(queryParams);`,
      output: `API.path('foo').query(queryParams).delete();`,
      errors: [
        'Passing parameters to API.delete() is deprecated.  Migrate from .delete(queryparams) to .query(queryparams).delete()',
      ],
    },
    // .delete() doesn't have queryparams args
    {
      code: `API.path('foo').path(id);`,
      output: `API.path('foo', id);`,
      errors: ["Prefer API.path('foo', 'bar') over API.path('foo').path('bar')"],
    },
    // Migrate from API to REST()
    {
      code: `API.path('foo', id).withCache().get()`,
      output: `REST().path('foo', id).withCache().get()`,
      errors: ['API is deprecated, switch to REST()'],
    },
  ],
});
