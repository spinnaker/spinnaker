import { render, screen } from '@testing-library/react';
import React from 'react';

import { CanaryScores } from './CanaryScores';

import '@spinnaker/core/presentation/main.less';

const dangerBorderColor = 'rgb(255, 0, 0)';

describe('<CanaryScores />', () => {
  let previousDangerColor: string;

  beforeAll(() => {
    previousDangerColor = document.documentElement.style.getPropertyValue('--color-danger');
    document.documentElement.style.setProperty('--color-danger', dangerBorderColor);
  });

  afterAll(() => {
    document.documentElement.style.setProperty('--color-danger', previousDangerColor);
  });

  it('styles directly invalid score inputs', () => {
    render(<CanaryScores successfulScore="50" unhealthyScore="60" onChange={vi.fn()} />);
    const inputs = screen.getAllByRole('spinbutton');

    expect(inputs[0]).toHaveClass('form-control', 'invalid');
    expect(inputs[0]).not.toHaveClass('dirty');
    expect(inputs[1]).toHaveClass('invalid');
    expect(window.getComputedStyle(inputs[0]).borderColor).toBe(dangerBorderColor);
  });
});
