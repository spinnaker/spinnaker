import { shallow } from 'enzyme';
import React from 'react';

import { GceInstanceFlexibilityPolicyDetails } from './GceInstanceFlexibilityPolicyDetails';

describe('GceInstanceFlexibilityPolicyDetails', () => {
  it('renders selection names, optional ranks, and machine types without duplicating target shape', () => {
    const wrapper = shallow(
      <GceInstanceFlexibilityPolicyDetails
        instanceFlexibilityPolicy={{
          instanceSelections: {
            preferred: { machineTypes: ['n2-standard-8'] },
            fallback: { rank: 1, machineTypes: ['e2-standard-8', 'c3-standard-8'] },
          },
        }}
      />,
    );

    const text = wrapper.text();
    expect(text).toContain('preferred');
    expect(text).toContain('n2-standard-8');
    expect(text).toContain('fallback');
    expect(text).toContain('1');
    expect(text).toContain('e2-standard-8, c3-standard-8');
    expect(text).not.toContain('Target Shape');
  });

  it('renders nothing when the policy is absent', () => {
    const wrapper = shallow(<GceInstanceFlexibilityPolicyDetails />);
    expect(wrapper.type()).toBeNull();
  });
});
