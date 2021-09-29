import type { Rule } from 'eslint';
import type { CallExpression, ImportDeclaration, ImportSpecifier, MemberExpression, Node } from 'estree';
import * as _ from 'lodash/fp';

import {
  getCallChain,
  getCallingIdentifier,
  getProgram,
  getVariableInitializer,
  getVariableInScope,
} from '../utils/utils';

function isAPICall(context: Rule.RuleContext, node: CallExpression) {
  // Find the call chain Identifier:
  // API.one().all().get()
  // ^^^
  const callingIdentifier = getCallingIdentifier(node);
  if (callingIdentifier && callingIdentifier.name === 'API') {
    return true;
  }

  // If the calling identifier is a variable reference ...
  // var foo = API.one(); foo.one().all().get();
  //                      ^^^
  // then find the variable initializer: var foo = API.one();
  //                                               ^^^^^^^^^
  const variable = getVariableInScope(context, callingIdentifier);
  const initializer = getVariableInitializer(variable);
  const initializerIdentifier = getCallingIdentifier(initializer);

  return initializerIdentifier && initializerIdentifier.name === 'API';
}

const getCallName = _.get('callee.property.name');

function isCallNamed(...callNames: string[]) {
  return function (callExpression: CallExpression) {
    const callName = getCallName(callExpression);
    return callNames.includes(callName);
  };
}

/**
 * Returns a list of deprecated methods that need to be renamed and fixers to do so.
 */
function findMethodsToRename(callChain: CallExpression[]) {
  const renames = {
    getList: 'get',
    withParams: 'query',
    one: 'path',
    all: 'path',
    remove: 'delete',
  };

  const calls = callChain.map((call) => {
    const from = getCallName(call) as keyof typeof renames;
    const to = renames[from];
    const fix = (fixer: Rule.RuleFixer) => fixer.replaceText((call.callee as MemberExpression).property, to);
    return { call, fix, from, to };
  });

  // Only return calls that have a 'to' mapping in the renames object
  return calls.filter((tuple) => !!tuple.to);
}

interface IRename {
  from: string;
  to: string;
  fix: (fixer: Rule.RuleFixer) => Rule.Fix;
}

function reportSimpleRenames(context: Rule.RuleContext, node: Node, renames: IRename[]) {
  // Strings for the message
  const froms = [...new Set(renames.map((tuple) => tuple.from))].join('/');
  const tos = [...new Set(renames.map((tuple) => tuple.to))].join('/');

  return context.report({
    node,
    message: `API.${froms}() is deprecated.  Migrate from ${froms}() to ${tos}()`,
    fix: (fixer) => renames.map((tuple) => tuple.fix(fixer)),
  });
}

function reportDataMethod(context: Rule.RuleContext, node: Node, callChain: CallExpression[]) {
  // Just the .data() calls
  const dataCalls = callChain.filter(isCallNamed('data'));

  // Find the corresponding put/post
  const putOrPost = callChain.find((n) => ['put', 'post'].includes((n.callee as any).property.name));
  const message = `API.data() is deprecated.  Migrate from .data({}) to .put({}) or .post({})`;

  // If there is a single .data() and a .put() or .post() in the chain...
  if (dataCalls.length !== 1 || !putOrPost) {
    // Can't find a single .data({}) and .post()/.put() to auto-fix, so just report the problem to the user
    return context.report({ node, message });
  }

  const call = dataCalls[0];
  // get the text of the arguments passed to .data(ARGS)
  const argsText = call.arguments.map((arg) => context.getSourceCode().getText(arg)).join(', ');
  // @ts-ignore find the spot between the parentheses in -> post()
  const putOrPostRangeEnd = putOrPost.callee.property.range[1] + 1;
  // @ts-ignore Just after ".one()" in `.one().data(value)`
  const previousCalleeRangeEnd = call.callee.object.range[1];
  // The end of `.data(value)`
  const dataRangeEnd = call.range[1];
  return context.report({
    node,
    message,
    fix: (fixer) => [
      // Move "value" text from .data(value) into the put/post args, i.e.: .put(value)
      fixer.replaceTextRange([putOrPostRangeEnd, putOrPostRangeEnd], argsText),
      // Remove .data(value) entirely
      fixer.replaceTextRange([previousCalleeRangeEnd, dataRangeEnd], ''),
    ],
  });
}

function reportGetsAndDeletesWithArgs(context: Rule.RuleContext, node: Node, getsAndDeletes: CallExpression[]) {
  const callNames = [...new Set(getsAndDeletes.map(getCallName))].join('/');
  const message = `Passing parameters to API.${callNames}() is deprecated.  Migrate from .${callNames}(queryparams) to .query(queryparams).${callNames}()`;

  // Should be only one get/delete, but just in case, report without fixing:
  if (getsAndDeletes.length > 1) {
    return context.report({ node, message });
  }

  const call = getsAndDeletes[0];
  const type = getCallName(call);
  const argsText = call.arguments.map((arg) => context.getSourceCode().getText(arg)).join(', ');
  const getCallStart = (call.callee as MemberExpression).property.range[0];
  const getCallEnd = call.range[1];
  const fix = (fixer: Rule.RuleFixer) =>
    fixer.replaceTextRange([getCallStart, getCallEnd], `query(${argsText}).${type}()`);

  return context.report({ node, message, fix });
}

function reportChainedPathAsVarargs(callChain: CallExpression[], context: Rule.RuleContext, node: Node) {
  const message = `Prefer API.path('foo', 'bar') over API.path('foo').path('bar')`;

  const fix = (fixer: Rule.RuleFixer) => {
    const [firstPathCall, secondPathCall] = callChain;

    const firstPathLastArg = firstPathCall['arguments'].slice().pop();
    const secondPathStart = firstPathCall.range[1];
    const secondPathEnd = secondPathCall.range[1];
    const secondPathArgsText = secondPathCall['arguments']
      .map((arg) => context.getSourceCode().getText(arg))
      .join(', ');

    return [
      // Move the second .path(...) call's args to first .path() args list
      fixer.insertTextAfter(firstPathLastArg, `, ${secondPathArgsText}`),
      // Remove second .path()
      fixer.removeRange([secondPathStart, secondPathEnd]),
    ];
  };

  return context.report({ message, node, fix });
}

function reportAPIDeprecatedUseREST(node: Node, context: Rule.RuleContext, callChain: CallExpression[]) {
  // Everything else is migrated, now migrate from API.path() to REST().path()
  const message = 'API is deprecated, switch to REST()';
  const API = (callChain[0].callee as MemberExpression).object;
  const program = getProgram(node);
  const allImports = program.body.filter((item) => item.type === 'ImportDeclaration') as ImportDeclaration[];
  const importSpecifiers = allImports
    .map((decl) => decl.specifiers)
    .reduce((acc, x) => acc.concat(x), []) as ImportSpecifier[]; //flatten

  const apiImport = importSpecifiers.find((specifier) => {
    return specifier.imported && specifier.imported?.name === 'API';
  });

  return context.report({
    message,
    node,
    fix: (fixer) => {
      if (!apiImport) {
        // Replace API with REST()
        return fixer.replaceText(API, 'REST()');
      }

      return [
        // Replace API with REST()
        fixer.replaceText(API, 'REST()'),
        fixer.replaceText(apiImport, 'REST'),
      ];
    },
  });
}

const rule: Rule.RuleModule = {
  create(context) {
    return {
      /**
       * Look for chains of CallExpressions that are:
       * - part of an API.xyz() call, e.g.: return API.xyz().get()
       * - part of an xyz() call chained off a variable, e.g.: var foo = API.xyz(); foo.get()
       * @param node {CallExpression}
       */
      CallExpression(node) {
        if (node.parent.type === 'MemberExpression' || !isAPICall(context, node)) {
          return undefined;
        }

        // an array of CallExpressions, i.e. for API.one().all().get() -> [.one, .all, .get]
        const callChain = getCallChain(node);

        // Migrate the simple method renames, i.e.: API.one() -> API.path()
        const renames = findMethodsToRename(callChain);
        if (renames.length) {
          return reportSimpleRenames(context, node, renames);
        }

        // Migrate .data(postdata).post() -> .post(postdata)
        // Migrate .data(putdata).put() -> .put(putdata)
        if (callChain.some(isCallNamed('data'))) {
          return reportDataMethod(context, node, callChain);
        }

        // Migrate .get(params) -> .query(params).get()
        // Migrate .delete(params) -> .query(params).delete()
        const getsAndDeletes = callChain.filter(isCallNamed('get', 'delete'));
        if (getsAndDeletes.some((x) => x.arguments.length > 0)) {
          return reportGetsAndDeletesWithArgs(context, node, getsAndDeletes);
        }

        // Migrate .path('foo').path('bar') -> .path('foo', 'bar')
        const hasTwoChainedPathCalls = getCallName(callChain[0]) === 'path' && getCallName(callChain[1]) === 'path';
        if (hasTwoChainedPathCalls) {
          return reportChainedPathAsVarargs(callChain, context, node);
        }

        // Migrate from API.xyz() to REST().xyz()
        const callingIdentifier = getCallingIdentifier(node);
        if (callingIdentifier && callingIdentifier.name === 'API') {
          return reportAPIDeprecatedUseREST(node, context, callChain);
        }
      },
    };
  },
  meta: {
    fixable: 'code',
    type: 'problem',
    docs: {
      description: 'Migrate from API.xyz() to REST(path)',
    },
  },
};

export default rule;
