import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { render, screen } from '@testing-library/react';
import React from 'react';

import { webhookExecutionDetailsSections } from './WebhookExecutionDetails';

describe('WebhookExecutionDetails', () => {
  const WebhookConfigSection = webhookExecutionDetailsSections[0] as any;
  const renderSection = (stage: any) => {
    const router = new UIRouterReact();
    return render(
      <UIRouterContext.Provider value={router}>
        <WebhookConfigSection current="webhookConfig" name="webhookConfig" stage={stage} />
      </UIRouterContext.Provider>,
    );
  };

  it('renders copy controls for payload and response', () => {
    const stage = {
      context: {
        payload: { hello: 'world' },
        webhook: { body: { ok: true } },
      },
      originalStatus: 'SUCCEEDED',
    } as any;

    const { container } = renderSection(stage);
    const copyValues = Array.from(container.querySelectorAll('textarea[tabindex="-1"]')).map(
      (textarea: HTMLTextAreaElement) => textarea.value,
    );

    expect(screen.getAllByRole('button', { name: 'Copy to clipboard' })).toHaveLength(2);
    expect(copyValues).toEqual([
      JSON.stringify(stage.context.payload, null, 2),
      JSON.stringify(stage.context.webhook.body, null, 2),
    ]);
  });

  it('renders status endpoint and progress urls as safe links', () => {
    const stage = {
      context: {
        statusEndpoint: 'https://example.test/status/1',
        waitForCompletion: true,
        webhook: {
          monitor: {
            progressMessage: 'See https://example.test/progress/1\nIgnore javascript:alert(1)',
          },
        },
      },
      originalStatus: 'RUNNING',
      status: 'RUNNING',
    } as any;

    const { container } = renderSection(stage);
    const links = screen.getAllByRole('link');

    expect(links[0]).toHaveAttribute('href', 'https://example.test/status/1');
    expect(links[0]).toHaveAttribute('target', '_blank');
    expect(links[0]).toHaveAttribute('rel', 'noopener noreferrer');
    expect(links[1]).toHaveAttribute('href', 'https://example.test/progress/1');
    expect(container.querySelector('.webhook-progress-message')).toHaveStyle({ whiteSpace: 'pre-line' });
    expect(links.map((link) => link.getAttribute('href'))).not.toContain('javascript:alert(1)');
  });
});
