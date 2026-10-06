import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { GceAutoHealingPolicyEditor } from './GceAutoHealingPolicyEditor';
import { IGceHealthCheckKind } from '../domain';

describe('GceAutoHealingPolicyEditor', () => {
  it('loads only health checks from the selected account', async () => {
    const reader = {
      listHealthChecks: vi.fn().mockReturnValue(
        Promise.resolve([
          {
            account: 'my-account',
            name: 'web',
            kind: IGceHealthCheckKind.healthCheck,
            selfLink: 'https://compute/healthChecks/web',
          },
          {
            account: 'other-account',
            name: 'other',
            kind: IGceHealthCheckKind.httpHealthCheck,
            selfLink: 'https://compute/httpHealthChecks/other',
          },
        ]),
      ),
    };
    render(
      <GceAutoHealingPolicyEditor account="my-account" policy={{}} onChange={() => undefined} reader={reader as any} />,
    );

    const select = await screen.findByTestId('health-check');
    await waitFor(() => expect(reader.listHealthChecks).toHaveBeenCalled());
    expect(within(select).getAllByRole('option')).toHaveLength(2);
    expect(select).toHaveTextContent('web');
    expect(select).not.toHaveTextContent('other');
  });

  it('writes the selected health check name and kind from its URL', async () => {
    const onChange = vi.fn();
    const reader = {
      listHealthChecks: vi.fn().mockResolvedValue([
        {
          account: 'my-account',
          kind: IGceHealthCheckKind.httpHealthCheck,
          name: 'web',
          selfLink: 'https://compute/httpHealthChecks/web',
        },
      ]),
    };
    render(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={{ initialDelaySec: 0 }}
        onChange={onChange}
        reader={reader as any}
      />,
    );

    await waitFor(() => expect(screen.getByTestId('health-check')).toBeEnabled());
    fireEvent.change(screen.getByTestId('health-check'), {
      target: { value: 'https://compute/httpHealthChecks/web' },
    });

    expect(onChange).toHaveBeenCalledWith({
      initialDelaySec: 0,
      healthCheckUrl: 'https://compute/httpHealthChecks/web',
      healthCheck: 'web',
      healthCheckKind: IGceHealthCheckKind.httpHealthCheck,
    });
  });

  it('normalizes an existing healthCheck URL into its URL, name, and kind fields', async () => {
    const onChange = vi.fn();
    const reader = {
      listHealthChecks: vi.fn().mockReturnValue(Promise.resolve([])),
    };
    render(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={{ healthCheck: 'https://compute/httpHealthChecks/web', initialDelaySec: 0 }}
        onChange={onChange}
        reader={reader as any}
      />,
    );

    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith({
        healthCheckUrl: 'https://compute/httpHealthChecks/web',
        healthCheck: 'web',
        healthCheckKind: IGceHealthCheckKind.httpHealthCheck,
        initialDelaySec: 0,
      }),
    );
  });

  it('resolves an existing healthCheck name to the matching URL and kind', async () => {
    const onChange = vi.fn();
    const reader = {
      listHealthChecks: vi.fn().mockReturnValue(
        Promise.resolve([
          {
            account: 'my-account',
            name: 'web',
            kind: IGceHealthCheckKind.healthCheck,
            selfLink: 'https://compute/healthChecks/web',
          },
        ]),
      ),
    };
    render(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={{ healthCheck: 'web', initialDelaySec: 0 }}
        onChange={onChange}
        reader={reader as any}
      />,
    );

    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith({
        healthCheckUrl: 'https://compute/healthChecks/web',
        healthCheck: 'web',
        healthCheckKind: IGceHealthCheckKind.healthCheck,
        initialDelaySec: 0,
      }),
    );
  });

  it('preserves zero for initial delay without rendering legacy max unavailable controls', async () => {
    const onChange = vi.fn();
    const reader = { listHealthChecks: vi.fn().mockResolvedValue([]) };
    const legacyPolicy = { initialDelaySec: 0, maxUnavailable: { percent: 0 } };
    const { container } = render(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={legacyPolicy as any}
        onChange={onChange}
        reader={reader as any}
      />,
    );

    await waitFor(() => expect(screen.getByTestId('health-check')).toBeEnabled());
    expect(reader.listHealthChecks).toHaveBeenCalled();
    expect(screen.getByTestId('initial-delay')).toHaveValue(0);
    expect(screen.queryByTestId('max-unavailable')).not.toBeInTheDocument();
    expect(screen.queryByTestId('max-unavailable-unit')).not.toBeInTheDocument();
    expect(container).not.toHaveTextContent('Max unavailable');
  });
});
