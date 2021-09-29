import type { Rule } from 'eslint';
import type { CallExpression, Identifier, Literal } from 'estree';
import { get } from 'lodash';

import { getCallingIdentifier, getVariableInScope, isMemberExpression } from '../utils/utils';

/**
 * No slashes in string literals passed to API.one() / API.all()
 *
 * @version 0.1.0
 * @category
 */
const rule = function (context: Rule.RuleContext) {
  return {
    CallExpression: function (node: CallExpression) {
      const callee = node.callee;
      const args = node.arguments;

      // .one() or .all()
      const propertyName =
        (isMemberExpression(callee) && callee.property && 'name' in callee.property && callee.property.name) || '';

      if (propertyName !== 'one' && propertyName !== 'all') {
        // console.log('not one or all');
        return;
      }

      // API.all('ok').one('ok', 'foo/bad', 'ok')
      // ^^^
      if ((getCallingIdentifier(node) || {}).name !== 'API') {
        // console.log(getCallingIdentifier(callee));
        // console.log('calling identifier not API');
        return;
      }

      // Get the source code (think .toString()) of the AST node and find a slash
      // This isn't 100% accurate, but it's good enough.
      function sourceCodeHasSlash(node) {
        const text = node ? context.getSourceCode().getText(node) : '';
        const splitOnSlash = /\.split\(['"`]\/['"]\)/;
        return !!(text && text.includes('/') && !splitOnSlash.exec(text));
      }

      function isArgLiteralWithSlash(arg): arg is Literal {
        return arg.type === 'Literal' && sourceCodeHasSlash(arg);
      }

      const isVariableInitializedWithSlash = (arg): arg is Identifier => {
        if (arg.type !== 'Identifier') {
          return false;
        }

        // Check if the arg is a variable
        const variable = getVariableInScope(context, arg);
        // Find the variable's initializer
        const initializer = get(variable, 'defs[0].node.init', null);
        // Check if that variable's initializer contains a slash
        return sourceCodeHasSlash(initializer);
      };

      // Literal:
      // .one(okArg, 'foo/' + barid)
      const literalsWithSlashes = args.filter((arg) => isArgLiteralWithSlash(arg)) as Literal[];

      // Initializer:
      // var badArg = `foo/${barid}`;
      // var badArg2 = 'foo/' + barid`;
      // .one(badArg)
      // .one(badArg2)
      const varsWithSlashes = args.filter((arg) => isVariableInitializedWithSlash(arg)) as Identifier[];

      // Expression:
      // .one(`foo/${barid}`)
      const expressionWithSlash = args.filter(
        (arg) => !literalsWithSlashes.includes(arg as any) && sourceCodeHasSlash(arg),
      );

      if (literalsWithSlashes.length === 0 && varsWithSlashes.length === 0 && expressionWithSlash.length === 0) {
        return;
      }

      const message =
        `Do not include slashes in API.one() or API.all() calls because arguments to .one() and .all() get url encoded.` +
        `Instead, of API.one('foo/bar'), split into multiple arguments: API.one('foo', 'bar').`;

      const fix = (fixer) => {
        // within:
        //   API.one('foo/bad')
        // replaces:
        //   'foo/bad'
        // with:
        //   'foo', 'bad'
        const literalArgFixes = literalsWithSlashes.map((arg) => {
          const varArgs = (arg.value as string)
            .split('/')
            .map((segment) => "'" + segment + "'")
            .join(', ');
          return fixer.replaceText(arg, varArgs);
        });

        // within:
        //   let myVar = 'foo/bad';
        //   API.one(myVar)
        // replaces argument:
        //   myVar
        // with:
        //   ...myVar.split('/')
        // i.e.:
        //   API.one(...myVar.split('/'))
        const variableArgFixes = varsWithSlashes.map((arg) => {
          // Found a variable with an initializer containing a slash
          // Change the argument to be a string-split + array-spread
          const spread = `...${arg.name}.split('/')`;
          return fixer.replaceText(arg, spread);
        });

        // within:
        //   let prefix = 'foo/bad';
        //   API.one(`${prefix}/path`)
        // replaces with:
        //   `${prefix}/path`
        // with:
        //   ...(`${prefix}/path`).split('/')
        // i.e.:
        //   API.one(...(`${prefix}/path`).split('/'))
        const expressionWithSlashFixes = expressionWithSlash.map((arg) => {
          const text = context.getSourceCode().getText(arg);
          const spread = `...(${text}).split('/')`;
          return fixer.replaceText(arg, spread);
        });

        return literalArgFixes.concat(variableArgFixes).concat(expressionWithSlashFixes);
      };

      context.report({ fix, node, message });
    },
  };
};

const ruleModule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    fixable: 'code',
  },
  create: rule,
};

export default ruleModule;
