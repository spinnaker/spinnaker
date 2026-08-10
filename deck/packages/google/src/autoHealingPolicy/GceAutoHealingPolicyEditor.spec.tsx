import React from 'react';
import { shallow } from 'enzyme';

import { IGceHealthCheckKind } from '../domain';
import { GceAutoHealingPolicyEditor } from './GceAutoHealingPolicyEditor';

describe('GceAutoHealingPolicyEditor', () => {
  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('loads only health checks from the selected account', async () => {
    const reader = {
      listHealthChecks: jasmine.createSpy('listHealthChecks').and.returnValue(
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
    const wrapper = shallow(
      <GceAutoHealingPolicyEditor account="my-account" policy={{}} onChange={() => undefined} reader={reader as any} />,
    );

    await flush();
    wrapper.update();

    expect(wrapper.find('[data-testid="health-check"] option').length).toBe(2);
    expect(wrapper.find('[data-testid="health-check"]').text()).toContain('web');
    expect(wrapper.find('[data-testid="health-check"]').text()).not.toContain('other');
  });

  it('writes the selected health check name and kind from its URL', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceAutoHealingPolicyEditor account="my-account" policy={{ initialDelaySec: 0 }} onChange={onChange} />,
      { disableLifecycleMethods: true },
    );

    wrapper.find('[data-testid="health-check"]').simulate('change', {
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
    const onChange = jasmine.createSpy('onChange');
    const reader = {
      listHealthChecks: jasmine.createSpy('listHealthChecks').and.returnValue(Promise.resolve([])),
    };
    shallow(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={{ healthCheck: 'https://compute/httpHealthChecks/web', initialDelaySec: 0 }}
        onChange={onChange}
        reader={reader as any}
      />,
    );

    await flush();

    expect(onChange).toHaveBeenCalledWith({
      healthCheckUrl: 'https://compute/httpHealthChecks/web',
      healthCheck: 'web',
      healthCheckKind: IGceHealthCheckKind.httpHealthCheck,
      initialDelaySec: 0,
    });
  });

  it('resolves an existing healthCheck name to the matching URL and kind', async () => {
    const onChange = jasmine.createSpy('onChange');
    const reader = {
      listHealthChecks: jasmine.createSpy('listHealthChecks').and.returnValue(
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
    shallow(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={{ healthCheck: 'web', initialDelaySec: 0 }}
        onChange={onChange}
        reader={reader as any}
      />,
    );

    await flush();

    expect(onChange).toHaveBeenCalledWith({
      healthCheckUrl: 'https://compute/healthChecks/web',
      healthCheck: 'web',
      healthCheckKind: IGceHealthCheckKind.healthCheck,
      initialDelaySec: 0,
    });
  });

  it('preserves zero for initial delay and max unavailable', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceAutoHealingPolicyEditor
        account="my-account"
        policy={{ initialDelaySec: 0, maxUnavailable: { percent: 0 } }}
        onChange={onChange}
      />,
      { disableLifecycleMethods: true },
    );

    expect(wrapper.find('[data-testid="initial-delay"]').prop('value')).toBe(0);
    expect(wrapper.find('[data-testid="max-unavailable"]').prop('value')).toBe(0);
    wrapper.find('[data-testid="max-unavailable-unit"]').simulate('change', { target: { value: 'fixed' } });

    expect(onChange).toHaveBeenCalledWith({ initialDelaySec: 0, maxUnavailable: { fixed: 0 } });
  });
});
