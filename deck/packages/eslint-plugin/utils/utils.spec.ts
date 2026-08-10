import type { Rule, Scope } from 'eslint';

import { getVariableInScope } from './utils';

describe('getVariableInScope', () => {
  test('uses the legacy context scope API when SourceCode does not provide one', () => {
    const variable = {} as Scope.Variable;
    const context = ({
      getScope: () => ({
        references: [{ identifier: { name: 'value' }, resolved: variable }],
      }),
    } as unknown) as Rule.RuleContext;

    expect(getVariableInScope(context, { name: 'value', type: 'Identifier' })).toBe(variable);
  });
});
