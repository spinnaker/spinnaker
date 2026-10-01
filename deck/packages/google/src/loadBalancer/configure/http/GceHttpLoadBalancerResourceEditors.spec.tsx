import { fireEvent, render, within } from '@testing-library/react';
import React from 'react';

import {
  GceHttpLoadBalancerBackendServiceEditor,
  GceHttpLoadBalancerHealthCheckEditor,
} from './GceHttpLoadBalancerResourceEditors';

describe('GceHttpLoadBalancerResourceEditors', () => {
  it('edits complete health-check objects without dropping unknown fields', () => {
    const onChange = vi.fn();
    const { getByTestId, getAllByRole } = render(
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
        onRemove={vi.fn()}
      />,
    );

    fireEvent.change(getByTestId('health-check-port'), { target: { value: '8080' } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'check-a', port: 8080, requestPath: '/health', unknownField: 'keep' }),
    );
    expect(getAllByRole('button').every((button) => button.getAttribute('type') === 'button')).toBe(true);
  });

  it('supports HTTP2 request paths and GRPC service names', () => {
    const onChange = vi.fn();
    const { getByTestId, queryByTestId, rerender } = render(
      <GceHttpLoadBalancerHealthCheckEditor
        healthCheck={{ healthCheckType: 'GRPC', name: 'grpc-check', port: 443 }}
        healthChecks={[]}
        onChange={onChange}
        onRemove={vi.fn()}
      />,
    );

    expect(optionValues(getByTestId('health-check-protocol'))).toContain('HTTP2');
    expect(optionValues(getByTestId('health-check-protocol'))).toContain('GRPC');
    expect(getByTestId('health-check-grpc-service-name')).toBeInTheDocument();
    expect(queryByTestId('health-check-path')).not.toBeInTheDocument();

    fireEvent.change(getByTestId('health-check-grpc-service-name'), { target: { value: 'new.Service' } });
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ grpcServiceName: 'new.Service', healthCheckType: 'GRPC' }),
    );

    rerender(
      <GceHttpLoadBalancerHealthCheckEditor
        healthCheck={{ healthCheckType: 'HTTP2', name: 'http2-check', port: 443, requestPath: '/ready' }}
        healthChecks={[]}
        onChange={onChange}
        onRemove={vi.fn()}
      />,
    );

    expect(getByTestId('health-check-path')).toHaveValue('/ready');
    expect(queryByTestId('health-check-grpc-service-name')).not.toBeInTheDocument();
  });

  it('selects complete backend-service and health-check references', () => {
    const onChange = vi.fn();
    const backendServices = [
      {
        healthCheck: { name: 'check-a', selfLink: 'https://compute/healthChecks/check-a' },
        name: 'backend-a',
        portName: 'http',
        sessionAffinity: 'NONE',
        unknownField: 'keep',
      },
    ];
    const { getByTestId, getAllByRole } = render(
      <GceHttpLoadBalancerBackendServiceEditor
        backendService={{ name: '' }}
        backendServices={backendServices}
        healthChecks={[{ name: 'check-a', selfLink: 'https://compute/healthChecks/check-a' }]}
        loadBalancerType="HTTP"
        onChange={onChange}
        onRemove={vi.fn()}
      />,
    );

    fireEvent.change(getByTestId('backend-service-reference'), {
      target: { value: 'backend-a' },
    });

    expect(onChange).toHaveBeenCalledWith(backendServices[0]);
    expect(getAllByRole('button').every((button) => button.getAttribute('type') === 'button')).toBe(true);
  });
});

function optionValues(select: HTMLElement): string[] {
  return within(select)
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value);
}
