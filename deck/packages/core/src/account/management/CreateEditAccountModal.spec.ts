import { validateDefinitionStructure } from './CreateEditAccountModal';
import { ACCOUNT_SAMPLES, buildSampleDefinition } from './accountSamples';

describe('validateDefinitionStructure', () => {
  it('rejects malformed JSON', () => {
    expect(validateDefinitionStructure('{ not json')).toContain('Invalid JSON');
  });

  it('rejects non-object documents', () => {
    expect(validateDefinitionStructure('[]')).toBe('Account definition must be a JSON object');
    expect(validateDefinitionStructure('"kubernetes"')).toBe('Account definition must be a JSON object');
    expect(validateDefinitionStructure('null')).toBe('Account definition must be a JSON object');
  });

  it('requires a non-empty type', () => {
    expect(validateDefinitionStructure('{"name": "prod"}')).toContain('"type"');
    expect(validateDefinitionStructure('{"type": " ", "name": "prod"}')).toContain('"type"');
  });

  it('requires a non-empty name', () => {
    expect(validateDefinitionStructure('{"type": "kubernetes"}')).toContain('"name"');
    expect(validateDefinitionStructure('{"type": "kubernetes", "name": ""}')).toContain('"name"');
  });

  it('rejects names with invalid characters', () => {
    expect(validateDefinitionStructure('{"type": "kubernetes", "name": "bad name!"}')).toContain(
      'letters, numbers, periods, hyphens and underscores',
    );
  });

  it('rejects renaming an existing account', () => {
    const existing = { type: 'kubernetes', name: 'old-name' };
    expect(validateDefinitionStructure('{"type": "kubernetes", "name": "new-name"}', existing)).toBe(
      'The account name cannot be changed after creation',
    );
    expect(validateDefinitionStructure('{"type": "kubernetes", "name": "old-name"}', existing)).toBeNull();
  });

  it('accepts a structurally valid definition', () => {
    expect(validateDefinitionStructure('{"type": "kubernetes", "name": "prod", "context": "ctx"}')).toBeNull();
  });

  it('accepts every bundled sample once a name is supplied', () => {
    Object.keys(ACCOUNT_SAMPLES).forEach((type) => {
      expect(validateDefinitionStructure(buildSampleDefinition(type, 'my-account'))).toBeNull();
    });
  });
});
