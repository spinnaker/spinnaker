import { render } from '@testing-library/react';
import React from 'react';

import { GceInstanceFlexibilityPolicyDetails } from './GceInstanceFlexibilityPolicyDetails';

describe('GceInstanceFlexibilityPolicyDetails', () => {
  it('renders selection names, optional ranks, and machine types without duplicating target shape', () => {
    const { container } = render(
      <GceInstanceFlexibilityPolicyDetails
        instanceFlexibilityPolicy={{
          instanceSelections: {
            preferred: { machineTypes: ['n2-standard-8'] },
            fallback: { rank: 1, machineTypes: ['e2-standard-8', 'c3-standard-8'] },
          },
        }}
      />,
    );

    expect(container).toHaveTextContent('preferred');
    expect(container).toHaveTextContent('n2-standard-8');
    expect(container).toHaveTextContent('fallback');
    expect(container).toHaveTextContent('1');
    expect(container).toHaveTextContent('e2-standard-8, c3-standard-8');
    expect(container).not.toHaveTextContent('Target Shape');
  });

  it('renders nothing when the policy is absent', () => {
    const { container } = render(<GceInstanceFlexibilityPolicyDetails />);
    expect(container).toBeEmptyDOMElement();
  });
});
