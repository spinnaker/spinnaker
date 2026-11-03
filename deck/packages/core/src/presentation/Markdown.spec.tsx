import { render as rtlRender } from '@testing-library/react';
import React from 'react';

import { Markdown } from './Markdown';

// Markdown sets its content with dangerouslySetInnerHTML, so assert on the rendered DOM.
const render = (message: string): HTMLElement =>
  rtlRender(<Markdown message={message} />).container.firstElementChild as HTMLElement;

describe('<Markdown />', () => {
  it('renders links and emphasis', () => {
    const node = render('see [docs](https://spinnaker.io) for *details*');

    expect(node.querySelector('a').getAttribute('href')).toBe('https://spinnaker.io');
    expect(node.querySelector('em').textContent).toBe('details');
  });

  // Orca wraps text written by Kubernetes controllers in a code span (WaitForManifestStableTask
  // asInlineCode) so that failure messages show it literally.
  describe('code spans produced for cluster-written text', () => {
    const cases = [
      {
        message:
          "'m' in 'ns' for account a: `Degraded: [details](https://example.com) ![x](https://example.com/x.png)`",
        text: "'m' in 'ns' for account a: Degraded: [details](https://example.com) ![x](https://example.com/x.png)",
      },
      {
        message: 'failed: ``uses `kubectl` here``',
        text: 'failed: uses `kubectl` here',
      },
      {
        message: 'failed: `` `starts with a tick ``',
        text: 'failed: `starts with a tick',
      },
      {
        message: 'failed: `<b>html</b> & <img src=x onerror=alert(1)>`',
        text: 'failed: <b>html</b> & <img src=x onerror=alert(1)>',
      },
    ];

    cases.forEach(({ message, text }) => {
      it(`renders ${JSON.stringify(message)} literally`, () => {
        const node = render(message);

        expect(node.textContent.trim()).toBe(text);
        expect(node.querySelectorAll('a, img, b').length).toBe(0);
        expect(node.querySelectorAll('code').length).toBe(1);
      });
    });
  });
});
