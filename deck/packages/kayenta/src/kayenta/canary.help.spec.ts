import { templateExample } from './canary.help';
import { templateProviderVariables } from './edit/templateProviderVariables';

describe('canary template help', () => {
  it('renders every provider starter example from templateProviderVariables', () => {
    Object.keys(templateProviderVariables).forEach((serviceType) => {
      expect(templateExample(serviceType)).toContain(templateProviderVariables[serviceType].example.slice(0, 10));
    });
  });

  it('HTML-escapes angle brackets and ampersands', () => {
    templateProviderVariables.__test = { serviceType: '__test', variables: [], example: 'a<b && c>d' };
    expect(templateExample('__test')).toBe('a&lt;b &amp;&amp; c&gt;d');
    delete templateProviderVariables.__test;
  });
});
