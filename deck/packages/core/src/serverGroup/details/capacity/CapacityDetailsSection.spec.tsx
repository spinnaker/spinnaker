import { render, screen } from '@testing-library/react';
import React from 'react';

import { CapacityDetailsSection } from './CapacityDetailsSection';

describe('<CapacityDetailsSection/>', function () {
  it('when min === max, it should be displayed in simplemode', function () {
    render(<CapacityDetailsSection capacity={{ min: 9, max: 9, desired: 4 }} current={5} />);
    expect(screen.getByText('Min/Max', { selector: 'dt' })).toBeInTheDocument();
  });

  it('when min !== max, it should display the different min and max separately', function () {
    render(<CapacityDetailsSection capacity={{ min: 1, max: 9, desired: 4 }} current={5} />);
    expect(screen.getByText('Min', { selector: 'dt' })).toBeInTheDocument();
    expect(screen.getByText('Max', { selector: 'dt' })).toBeInTheDocument();
  });
});
