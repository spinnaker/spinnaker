import React from 'react';
import { shallow } from 'enzyme';

import {
  GceHttpLoadBalancerBackendServiceEditor,
  GceHttpLoadBalancerHealthCheckEditor,
} from './GceHttpLoadBalancerResourceEditors';

describe('GceHttpLoadBalancerResourceEditors', () => {
  it('edits complete health-check objects without dropping unknown fields', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceHttpLoadBalancerHealthCheckEditor
        healthCheck={{
          checkIntervalSec: 10,
          healthCheckType: 'HTTP',
          name: 'check-a',
          port: 80,
          requestPath: '/health',
          timeoutSec: 5,
          unknownField: 'keep',
        }}
        healthChecks={[{ name: 'check-a' }]}
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
      />,
    );

    wrapper.find('[data-testid="health-check-port"]').simulate('change', { target: { value: '8080' } });

    expect(onChange).toHaveBeenCalledWith(
      jasmine.objectContaining({ name: 'check-a', port: 8080, requestPath: '/health', unknownField: 'keep' }),
    );
    expect(wrapper.find('button').everyWhere((button) => button.prop('type') === 'button')).toBe(true);
  });

  it('supports HTTP2 request paths and GRPC service names', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceHttpLoadBalancerHealthCheckEditor
        healthCheck={{ healthCheckType: 'GRPC', name: 'grpc-check', port: 443 }}
        healthChecks={[]}
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
      />,
    );

    expect(
      wrapper.find('[data-testid="health-check-protocol"] option').map((option) => option.prop('value')),
    ).toContain('HTTP2');
    expect(
      wrapper.find('[data-testid="health-check-protocol"] option').map((option) => option.prop('value')),
    ).toContain('GRPC');
    expect(wrapper.find('[data-testid="health-check-grpc-service-name"]').length).toBe(1);
  });

  it('selects complete backend-service and health-check references', () => {
    const onChange = jasmine.createSpy('onChange');
    const backendServices = [
      {
        healthCheck: { name: 'check-a', selfLink: 'https://compute/healthChecks/check-a' },
        name: 'backend-a',
        portName: 'http',
        sessionAffinity: 'NONE',
        unknownField: 'keep',
      },
    ];
    const wrapper = shallow(
      <GceHttpLoadBalancerBackendServiceEditor
        backendService={{ name: '' }}
        backendServices={backendServices}
        healthChecks={[{ name: 'check-a', selfLink: 'https://compute/healthChecks/check-a' }]}
        loadBalancerType="HTTP"
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
      />,
    );

    wrapper.find('[data-testid="backend-service-reference"]').simulate('change', {
      target: { value: 'backend-a' },
    });

    expect(onChange).toHaveBeenCalledWith(backendServices[0]);
    expect(wrapper.find('button').everyWhere((button) => button.prop('type') === 'button')).toBe(true);
  });
});
