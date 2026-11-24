import React from 'react';
import { fireEvent, render, within } from '@testing-library/react';

import {
  GceHttpLoadBalancerHostRuleEditor,
  GceHttpLoadBalancerPathRuleEditor,
} from './GceHttpLoadBalancerRoutingEditor';

describe('GceHttpLoadBalancerRoutingEditor', () => {
  const backendServices = [
    { name: 'default-backend', selfLink: 'https://compute/backendServices/default-backend' },
    { name: 'api-backend', selfLink: 'https://compute/backendServices/api-backend' },
  ];

  it('adds and removes exact nested path-rule rows with non-submit buttons', () => {
    const onChange = vi.fn();
    const { getByTestId, getAllByRole } = render(
      <GceHttpLoadBalancerHostRuleEditor
        backendServices={backendServices}
        hostRule={{
          hostPatterns: ['api.example.com'],
          pathMatcher: {
            defaultService: backendServices[0],
            pathRules: [{ backendService: backendServices[1], paths: ['/v1'] }],
          },
        }}
        onChange={onChange}
        onRemove={vi.fn()}
      />,
    );

    fireEvent.click(getByTestId('add-path-rule'));

    expect(onChange).toHaveBeenCalledWith({
      hostPatterns: ['api.example.com'],
      pathMatcher: {
        defaultService: backendServices[0],
        pathRules: [{ backendService: backendServices[1], paths: ['/v1'] }, { paths: [] }],
      },
    });
    expect(getAllByRole('button').every((button) => button.getAttribute('type') === 'button')).toBe(true);
  });

  it('edits path lists and preserves complete unresolved backend references', () => {
    const onChange = vi.fn();
    const { getByTestId, getAllByRole } = render(
      <GceHttpLoadBalancerPathRuleEditor
        backendServices={backendServices}
        onChange={onChange}
        onRemove={vi.fn()}
        pathRule={{ backendService: backendServices[1], paths: ['/v1'] }}
      />,
    );

    fireEvent.change(getByTestId('path-rule-paths'), { target: { value: '/v1, /v2' } });
    fireEvent.change(getByTestId('path-rule-backend'), {
      target: { value: 'default-backend' },
    });

    expect(onChange.mock.calls[0][0]).toEqual({ backendService: backendServices[1], paths: ['/v1', '/v2'] });
    expect(onChange.mock.calls[1][0]).toEqual({ backendService: backendServices[0], paths: ['/v1'] });
    expect(getAllByRole('button').every((button) => button.getAttribute('type') === 'button')).toBe(true);
  });

  it('requires every path matcher to select an explicit default backend', () => {
    const { getByTestId } = render(
      <GceHttpLoadBalancerHostRuleEditor
        backendServices={backendServices}
        hostRule={{
          hostPatterns: ['api.example.com'],
          pathMatcher: { defaultService: backendServices[0], pathRules: [] },
        }}
        onChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    );
    const select = getByTestId('host-rule-default-backend');

    expect(select).toBeRequired();
    expect(
      within(select)
        .getAllByRole('option')
        .map((option) => (option as HTMLOptionElement).value),
    ).toEqual(['default-backend', 'api-backend']);
  });
});
