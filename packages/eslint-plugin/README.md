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

This `yarn create-rule` command will:

- Scaffolds a sample rule
- Scaffolds a test for the sample rule
- Adds the rule to the plugin (`eslint-plugin.ts`)
- Adds the rule as an "error" to the plugin's base config `base.config.js`)

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

A rule file exports a Rule.RuleContext object.

```ts
import { Rule } from 'eslint';
const rule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: { description: `Rule Description` },
    fixable: 'code',
  },
  create: function myRuleFunction(context: Rule.RuleContext) {
    return {
      // rule contents here
    };
  },
};
export default rule;
```

> See: the [official docs](https://eslint.org/docs/developer-guide/working-with-rules#rule-basics) in a couple ways.
>
> Spinnaker rules can be written in Typescript instead of CommonJS

`myRuleFunction` is a callback that receives an [eslint context](https://eslint.org/docs/developer-guide/working-with-rules#the-context-object) and returns an object containing callbacks for AST node types.

Each callback will be called when the parser encounters a node of that type.
When a lint violation is detected, the callback should report it to the context object.

```ts
import { Rule } from 'eslint';
import { SimpleLiteral } from 'estree';
//  ...
function myRuleFunction(context: Rule.RuleContext) {
  return {
    // This callback is called whenever a 'Literal' node is encountered
    Literal: function (literalNode: SimpleLiteral & Rule.NodeParentExtension) {
      if (literalNode.raw.includes('JenkinsX')) {
        // lint violation encountered; report it
        const message = 'String literals may not include JenkinsX';
        context.report({ node, message });
      }
    },
  };
}
```

> This example explicitly types the `context` and `literalNode` parameters, but these can be automatically inferred by Typescript

In addition to callbacks that trigger on a simple node type (`Literal` in the example above),
you can also trigger a callback using an [eslint selector](https://eslint.org/docs/developer-guide/selectors).

Think of an eslint selector as a CSS selector, but for an AST.
Selectors can reduce boilerplate while writing a rule, but more importantly they can potentially improve readability.

```ts
// Using a selector
function myRuleFunction(context: Rule.RuleContext) {
  return {
    // Find an ExpressionStatement
    // - that is a CallExpression
    //   - that has a callee object named 'React'
    //   - and has a callee property named 'useEffect'
    "ExpressionStatement > CallExpression[callee.object.name = 'React'][callee.property.name = 'useEffect']"(
      node: ExpressionStatement,
    ) {
      const message = 'Prefer bare useEffect() over React.useEffect()';
      context.report({ node, message });
    },
  };
}

// Not using a selector
function myRuleFunction(context: Rule.RuleContext) {
  return {
    ExpressionStatement(node) {
      const expression = node.expression;
      if (
        expression?.type === 'CallExpression' &&
        expression.callee.type === 'MemberExpression' &&
        expression.callee.object.name === 'React' &&
        expression.callee.property.name === 'useEffect'
      ) {
        const message = 'Prefer bare useEffect() over React.useEffect()';
        context.report({ node, message });
      }
    },
  };
}
```

> One downside of using eslint selectors is the node type is not automatically inferred in the callback.
> When using selectors, you should explicitly type the node parameter.

#### Test a rule

We run the tests using [Jest](http://jestjs.io/), but we do not use jest assertions.
Instead, we use the `RuleTester` API from eslint to define our assertions.

```ts
import { ruleTester } from '../utils/ruleTester';
import { rule } from './my-cool-rule';

ruleTester.run('my-cool-rule', rule, {
  valid: [
    /** code that doesn't trigger the rule */
  ],
  invalid: [
    /** code that triggers the rule */
  ],
});
```

Make sure to add at least one valid and one invalid test cases:

```ts
ruleTester.run('my-cool-rule', rule, {
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
 PASS  test/my-cool-rule.spec.js

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

```ts
Literal(literalNode) {
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

Review the [fixer api docs](https://eslint.org/docs/developer-guide/working-with-rules#applying-fixes) for more details.

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

### Publishing

After committing and pushing your new rule, bump the version in package.json (commit and push) and then run `npm publish` manually.
