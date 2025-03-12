import rule from './rest-prefer-static-strings-in-initializer';
import ruleTester from '../utils/ruleTester';

ruleTester.run('rest-prefer-static-strings-in-initializer', rule, {
  valid: [
    { code: "REST('/foo/bar').path(id).get()" },
    { code: "REST('/foo/bar').path().get()" },
    { code: "REST(id).path('foo').get()" },
  ],
  invalid: [
    {
      code: "REST().path('foo').path('bar')",
      output: "REST('/foo').path('bar')",
      errors: ["Prefer REST('/foo/bar') over REST().path('foo', 'bar')"],
    },
    {
      code: "REST('foo').path('bar').get()",
      output: "REST('foo/bar').get()",
      errors: ["Prefer REST('/foo/bar') over REST().path('foo', 'bar')"],
    },
    {
      // Process one path arg at a time
      code: "REST('foo').path('bar', 'baz').get()",
      output: "REST('foo/bar').path('baz').get()",
      errors: ["Prefer REST('/foo/bar') over REST().path('foo', 'bar')"],
    },
    {
      // Process one path arg at a time
      code: "REST('foo').path('bar', 'baz').get()",
      output: "REST('foo/bar').path('baz').get()",
      errors: ["Prefer REST('/foo/bar') over REST().path('foo', 'bar')"],
    },
  ],
});
