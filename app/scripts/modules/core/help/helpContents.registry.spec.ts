import {mock} from 'angular';
import helpRegistryModule, {HelpContentsRegistry} from './helpContents.registry';

describe('Help contents registry', () => {

  let registry: HelpContentsRegistry;

  beforeEach(mock.module(helpRegistryModule));

  beforeEach(mock.inject((helpContentsRegistry: HelpContentsRegistry) => registry = helpContentsRegistry));

  describe('Override functionality', () => {
    it('overrides existing value', () => {
      registry.register('a', 'a');
      expect(registry.getHelpField('a')).toBe('a');

      registry.registerOverride('a', 'b');
      expect(registry.getHelpField('a')).toBe('b');
    });

    it('ignores subsequent registration or override registration once an override has been set', () => {
      registry.registerOverride('a', 'b');
      expect(registry.getHelpField('a')).toBe('b');

      registry.register('a', 'a');
      expect(registry.getHelpField('a')).toBe('b');

      registry.registerOverride('a', 'c');
      expect(registry.getHelpField('a')).toBe('b');
    });
  });
});
