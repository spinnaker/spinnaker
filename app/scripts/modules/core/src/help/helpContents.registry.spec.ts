import { HelpContentsRegistry } from './helpContents.registry';

describe('Help contents registry', () => {
  describe('Override functionality', () => {
    it('overrides existing value', () => {
      HelpContentsRegistry.register('a', 'a');
      expect(HelpContentsRegistry.getHelpField('a')).toBe('a');

      HelpContentsRegistry.registerOverride('a', 'b');
      expect(HelpContentsRegistry.getHelpField('a')).toBe('b');
    });

    it('ignores subsequent registration or override registration once an override has been set', () => {
      HelpContentsRegistry.registerOverride('a', 'b');
      expect(HelpContentsRegistry.getHelpField('a')).toBe('b');

      HelpContentsRegistry.register('a', 'a');
      expect(HelpContentsRegistry.getHelpField('a')).toBe('b');

      HelpContentsRegistry.registerOverride('a', 'c');
      expect(HelpContentsRegistry.getHelpField('a')).toBe('b');
    });
  });
});
