import { validateEvaluateVariablesStage } from './EvaluateVariablesStageConfig';
import type { IStage } from '../../../../domain';

function buildStage(variables: Array<{ key: string; value: string }>): IStage {
  return ({ variables } as unknown) as IStage;
}

describe('validateEvaluateVariablesStage', () => {
  describe('when dashed identifier support is disabled (default)', () => {
    it('rejects a variable name containing a hyphen', () => {
      const stage = buildStage([{ key: 'my-container-name', value: '${1}' }]);

      const result = validateEvaluateVariablesStage(stage);

      expect(result.variables[0].key).toEqual(
        'Error: Variable Name should consist only of letters, numbers, or underscore',
      );
    });

    it('accepts a variable name using only letters, numbers, and underscore', () => {
      const stage = buildStage([{ key: 'my_container_name1', value: '${1}' }]);

      const result = validateEvaluateVariablesStage(stage);

      expect(result.variables).toBeUndefined();
    });
  });

  describe('when dashed identifier support is enabled', () => {
    it('accepts a variable name containing a hyphen', () => {
      const stage = buildStage([{ key: 'my-container-name', value: '${1}' }]);

      const result = validateEvaluateVariablesStage(stage, true);

      expect(result.variables).toBeUndefined();
    });

    it('still rejects a variable name starting with a digit', () => {
      const stage = buildStage([{ key: '1-my-container-name', value: '${1}' }]);

      const result = validateEvaluateVariablesStage(stage, true);

      expect(result.variables[0].key).toEqual(
        'Error: Variable Name should consist only of letters, numbers, underscore, or hyphen',
      );
    });

    it('still rejects a variable name containing a space or other invalid character', () => {
      const stage = buildStage([{ key: 'my container name', value: '${1}' }]);

      const result = validateEvaluateVariablesStage(stage, true);

      expect(result.variables[0].key).toEqual(
        'Error: Variable Name should consist only of letters, numbers, underscore, or hyphen',
      );
    });
  });

  it('still enforces the duplicate key validator regardless of dashed identifier support', () => {
    const stage = buildStage([
      { key: 'my-name', value: '${1}' },
      { key: 'my-name', value: '${2}' },
    ]);

    const result = validateEvaluateVariablesStage(stage, true);

    expect(result.variables[0].key).toEqual("Duplicate key 'my-name'");
    expect(result.variables[1].key).toEqual("Duplicate key 'my-name'");
  });
});
