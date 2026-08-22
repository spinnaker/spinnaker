import React from 'react';
import { shallow } from 'enzyme';

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
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
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
        onRemove={jasmine.createSpy('onRemove')}
      />,
    );

    wrapper.find('[data-testid="add-path-rule"]').simulate('click');

    expect(onChange).toHaveBeenCalledWith({
      hostPatterns: ['api.example.com'],
      pathMatcher: {
        defaultService: backendServices[0],
        pathRules: [{ backendService: backendServices[1], paths: ['/v1'] }, { paths: [] }],
      },
    });
    expect(wrapper.find('button').everyWhere((button) => button.prop('type') === 'button')).toBe(true);
  });

  it('edits path lists and preserves complete unresolved backend references', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceHttpLoadBalancerPathRuleEditor
        backendServices={backendServices}
        onChange={onChange}
        onRemove={jasmine.createSpy('onRemove')}
        pathRule={{ backendService: backendServices[1], paths: ['/v1'] }}
      />,
    );

    wrapper.find('[data-testid="path-rule-paths"]').simulate('change', { target: { value: '/v1, /v2' } });
    wrapper.find('[data-testid="path-rule-backend"]').simulate('change', {
      target: { value: 'default-backend' },
    });

    expect(onChange.calls.argsFor(0)[0]).toEqual({ backendService: backendServices[1], paths: ['/v1', '/v2'] });
    expect(onChange.calls.argsFor(1)[0]).toEqual({ backendService: backendServices[0], paths: ['/v1'] });
    expect(wrapper.find('button').everyWhere((button) => button.prop('type') === 'button')).toBe(true);
  });

  it('requires every path matcher to select an explicit default backend', () => {
    const wrapper = shallow(
      <GceHttpLoadBalancerHostRuleEditor
        backendServices={backendServices}
        hostRule={{
          hostPatterns: ['api.example.com'],
          pathMatcher: { defaultService: backendServices[0], pathRules: [] },
        }}
        onChange={jasmine.createSpy('onChange')}
        onRemove={jasmine.createSpy('onRemove')}
      />,
    );
    const select = wrapper.find('[data-testid="host-rule-default-backend"]');

    expect(select.prop('required')).toBe(true);
    expect(select.find('option').map((option) => option.prop('value'))).toEqual(['default-backend', 'api-backend']);
  });
});
