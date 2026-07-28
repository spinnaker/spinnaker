import { shallow } from 'enzyme';
import React from 'react';

import { CopyToClipboard } from '../../../../utils';
import { webhookExecutionDetailsSections } from './WebhookExecutionDetails';

describe('WebhookExecutionDetails', () => {
  const WebhookConfigSection = webhookExecutionDetailsSections[0] as any;

  it('renders copy controls for payload and response', () => {
    const stage = {
      context: {
        payload: { hello: 'world' },
        webhook: { body: { ok: true } },
      },
      originalStatus: 'SUCCEEDED',
    } as any;

    const component = shallow(<WebhookConfigSection current="webhookConfig" name="webhookConfig" stage={stage} />)
      .find('ExecutionDetailsSection')
      .dive();

    expect(component.find(CopyToClipboard).length).toBe(2);
    expect(component.find(CopyToClipboard).at(0).prop('text')).toBe(JSON.stringify(stage.context.payload, null, 2));
    expect(component.find(CopyToClipboard).at(1).prop('text')).toBe(
      JSON.stringify(stage.context.webhook.body, null, 2),
    );
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

    const component = shallow(<WebhookConfigSection current="webhookConfig" name="webhookConfig" stage={stage} />)
      .find('ExecutionDetailsSection')
      .dive();
    const links = component.find('a');

    expect(links.at(0).prop('href')).toBe('https://example.test/status/1');
    expect(links.at(0).prop('target')).toBe('_blank');
    expect(links.at(0).prop('rel')).toBe('noopener noreferrer');
    expect(links.at(1).prop('href')).toBe('https://example.test/progress/1');
    expect(component.find('.webhook-progress-message').prop('style')).toEqual({ whiteSpace: 'pre-line' });
    expect(links.map((link) => link.prop('href'))).not.toContain('javascript:alert(1)');
  });
});
