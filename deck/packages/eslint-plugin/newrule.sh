#!/usr/bin/env bash
if [[ -z $1 ]] ; then
  echo "Enter name of rule, i.e.:"
  echo "$0 lint-rule-name"
  exit 1
fi

RULENAME=$1;


sed -e "s/^  rules:.*/&'@spinnaker\/${RULENAME}': 2,/" -i '' base.config.js
sed -e "s/^  rules:.*/&'${RULENAME}': require('.\/rules\/${RULENAME}'),/" -i '' eslint-plugin.js

npx prettier --write base.config.js eslint-plugin.js



# Write RULENAME.js
(
cat <<EndOfRuleFile
'use strict';
// @ts-check

/**
 * Import AST Types from 'estree'
 * @typedef {import('estree').CallExpression} CallExpression
 * @typedef {import('estree').ImportSpecifier} ImportSpecifier
 */

const _ = require('lodash/fp');

const { getProgram } = require('../utils/utils');

/** @type {RuleModule} */
module.exports = {
  create(context) {
    return {
      /** @param node {ImportSpecifier} */
      ImportSpecifier(node) {
        /** @type {ImportSpecifier[]} */
        if (node.local && node.local.name === 'API') {
          context.report({
            node,
            message: 'Do not import API',
            fix: (fixer) => fixer.remove(node),
          });
        }
      },
    };
  },
  meta: {
    fixable: 'code',
    type: 'problem',
    docs: {
      description: 'Do not import API',
    },
  },
};
EndOfRuleFile
) > rules/${RULENAME}.js


# Write RULENAME.spec.js
(
cat <<EndOfSpecFile
'use strict';

const ruleTester = require('../utils/ruleTester');
const rule = require('../rules/${RULENAME}');

ruleTester.run('${RULENAME}', rule, {
  valid: [
    {
      code: "import { Something } from 'somewhere';",
    },
  ],
  invalid: [
    {
      code: "import { API } from '@spinnaker/core';",
      output: "import {  } from '@spinnaker/core';",
      errors: ["Do not import API"],
    },
  ],
});

EndOfSpecFile
) > test/${RULENAME}.spec.js

