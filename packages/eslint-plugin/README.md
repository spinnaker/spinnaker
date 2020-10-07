# @spinnaker/eslint-plugin

This package is an ESLint plugin containing:

- A base ESLint config
  - Parser configured for typescript
  - A set of default plugins, e.g. `react-hooks` plugin
  - Recommended rule sets, e.g. `prettier/@typescript-eslint`
    - Specific from the recommended rule sets are disabled
- Custom ESLint rules specific to Spinnaker

### Use

To use the rules, create a `.eslintrc.js` containing:

```js
module.exports = {
  plugins: ['@spinnaker/eslint-plugin'],
  extends: ['plugin:@spinnaker/base'],
};
```

## Creating a custom lint rule

To create a rule, add the rule to `eslint-plugin/rules/my-rule.js` and add a test to `eslint-plugin/test/my-rule.spec.js`.

The rule should examine AST nodes to detect a lint violation.
Optionally, it can provide an automatic code fixer.

- Use https://astexplorer.net/ to generate the AST for a given code fragment.
  - Set the Language to `Javascript` and the parser to `@typescript-eslint/parser`
  - Paste a snippet of Javascript or Typescript, i.e. `API.one('foo/bar')`
- Using the interactive AST explorer, [write a rule](#write-a-rule) that detects the lint violation.
- [Write test cases](#test-a-rule) for valid and invalid input
- Optionally, [write a fixer](#write-a-fixer) and [write fixer tests](#test-a-fixer)
- [Add your rule to the plugin](#add-a-rule-to-the-plugin)

#### Write a rule

A rule file exports a rule metadata using CommonJS.
See: https://eslint.org/docs/developer-guide/working-with-rules#rule-basics

```js
module.exports = {
  meta: {
    type: 'problem',
    docs: { description: `Rule Description` },
    fixable: 'code',
  },
  create: myRuleFunction,
};
```

> Export the rule function `myRuleFunction` itself as the `.create` property.

The rule function is a callback that receives a `context` and returns an object containing callbacks for AST node types.

Each callback will be called when the parser encounters a node of that type.
When a lint violation is detected, the callback should report it to the context object.

```js
function myRuleFunction(context) {
  return {
    // This callback is called whenever a 'Literal' node is encountered
    Literal: function (literalNode) {
      if (literalNode.raw.includes('JenkinsX')) {
        // lint violation encountered; report it
        const message = 'String literals may not include JenkinsX';
        context.report({ node, message });
      }
    },
  };
}
```

#### Test a rule

Create a test file for your rule in `../test` following the naming convention.
Add some boilerplate:

```js
'use strict';
const ruleTester = require('../utils/ruleTester');
const rule = require('packages/eslint-plugin/rules/my-rule');
ruleTester.run('my-rule', rule, {
  valid: [],
  invalid: [],
});
```

Add at least one valid and one invalid test cases:

```js
ruleTester.run('my-rule', rule, {
  valid: [
    {
      code: 'var foo = "bar";',
    },
  ],
  invalid: [
    {
      code: 'var foo = "JenkinsX";',
      error: 'String literals may not include JenkinsX',
    },
    {
      code: 'createTodo("learn more about JenkinsX foundations");',
      error: 'String literals may not include JenkinsX',
    },
  ],
});
```

Run the tests from `/packages/eslint-plugin`:

```
❯ yarn test
yarn run v1.22.4
$ jest
 PASS  test/my-rule.spec.js

Test Suites: 1 passed, 1 total
Tests:       3 passed, 3 total
Snapshots:   0 total
Time:        1.095s
Ran all test suites.
✨  Done in 1.69s.
```

While writing tests, it's useful to run Jest in watch mode: `yarn test --watch`

If you need to debug your tests, run `yarn test:debug` and launch the Chrome Debugger
(enter `chrome://inspect` into the Chrome URL bar).

You can (and should) run your work-in-progress rule against the spinnaker OSS codebase:

```shell
./test_rule_against_deck_source.sh my-rule
```

### Write a fixer

Once your tests are passing, consider writing an auto-fixer.
Auto-fixers can be applied in downstream projects using `eslint --fix`.
An auto-fixer replaces AST nodes which violate the rule with non-violating code.

When reporting a lint violation for your rule, return a `fix` function.

```js
Literal: function(literalNode) {
  if (literalNode.raw.includes('JenkinsX')) {
    // lint violation encountered; report it
    const message = 'String literals may not include JenkinsX';
    const fix = (fixer) => {
      const fixedValue = literalNode.value.replaceAll('JenkinsX', 'JengaX');
      return fixer.replaceText(literalNode, '"' + fixedValue + '"');
    }
    context.report({ fix, node, message });
  }
}
```

See: https://eslint.org/docs/developer-guide/working-with-rules#applying-fixes
for details on the fixer api.

If you need to fix more than one thing for a given rule, you may return an array of fixes.

```js
const fix = (fixer) => {
  const fixedValue = literalNode.value.replaceAll('JenkinsX', 'JengaX');
  return [
    fixer.replaceText(literalNode, '"' + fixedValue '"'),
    fixer.insertTextBefore(literalNode, `/* Jengafied */ `),
  ]
}
```

### Test a fixer

The result of a fixer should be added to the tests.
Add an `output` key to all invalid test cases that can be auto-fixed.

```js
invalid: [
  {
    code: 'var foo = "JenkinsX";',
    error: 'String literals may not include JenkinsX',
    output: 'var foo = /* Jengafied */ "JengaX";',
  },
];
```

### Add a rule to the plugin

Add your rule to the plugin definition in `eslint-plugin.js`:

```js
module.exports = {
  rules: {
    'my-rule': require('./rules/my-rule'),
  },
  configs: {
    base: require('./base.config.js'),
    none: require('./none.config.js'),
  },
};
```

Then add the rule (via the plugin namespace) to `base.config.js`:

```js
module.exports = {
  parser: '@typescript-eslint/parser',
  parserOptions: { sourceType: 'module' },
  plugins: ['@typescript-eslint', '@spinnaker/eslint-plugin', 'react-hooks'],
  extends: ['eslint:recommended', 'prettier', 'prettier/@typescript-eslint', 'plugin:@typescript-eslint/recommended'],
  rules: {
    '@spinnaker/my-rule': 2,
    ... etc
  }
  ... etc
```
