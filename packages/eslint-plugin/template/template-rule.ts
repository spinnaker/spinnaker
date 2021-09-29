import type { Rule } from 'eslint';

const rule: Rule.RuleModule = {
  create: (context) => ({
    ImportSpecifier(node) {
      if (node.local.name === 'API') {
        const message = 'Do not import API';
        context.report({ node, message, fix: (fixer) => fixer.remove(node) });
      }
    },
  }),
  meta: {
    fixable: 'code',
    type: 'problem',
    docs: {
      description: 'Do not import API',
    },
  },
};

export default rule;
