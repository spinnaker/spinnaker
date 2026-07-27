import plugin from './eslint-plugin';

describe('ESLint config contracts', () => {
  test('provides flat and legacy base configs', () => {
    const configs = plugin.configs as Record<string, unknown>;

    expect(Array.isArray(configs.base)).toBe(true);
    expect(Array.isArray(configs['legacy-base'])).toBe(false);
    expect(configs['legacy-base']).toEqual(
      expect.objectContaining({
        plugins: expect.arrayContaining(['@spinnaker/eslint-plugin']),
        rules: expect.objectContaining({
          '@typescript-eslint/consistent-type-imports': ['error', { prefer: 'type-imports' }],
        }),
      }),
    );
  });

  test('omits the retired migration rule while preserving the generic API chaining rule', () => {
    const configs = plugin.configs as Record<string, unknown>;
    const flatRules = (configs.base as Array<{ rules?: Record<string, unknown> }>)
      .map((config) => config.rules)
      .filter((rules): rules is Record<string, unknown> => Boolean(rules));
    const legacyRules = (configs['legacy-base'] as { rules: Record<string, unknown> }).rules;

    expect(plugin.rules).not.toHaveProperty('migrate-to-mock-http-client');
    expect(flatRules.some((rules) => '@spinnaker/migrate-to-mock-http-client' in rules)).toBe(false);
    expect(legacyRules).not.toHaveProperty('@spinnaker/migrate-to-mock-http-client');

    expect(plugin.rules).toHaveProperty('api-no-unused-chaining');
    expect(flatRules.some((rules) => '@spinnaker/api-no-unused-chaining' in rules)).toBe(true);
    expect(legacyRules).toHaveProperty('@spinnaker/api-no-unused-chaining', 2);
  });
});
