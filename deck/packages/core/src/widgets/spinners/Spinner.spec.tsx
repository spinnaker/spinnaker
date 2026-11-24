import { render } from '@testing-library/react';
import React from 'react';

import { Spinner } from './Spinner';

describe('Spinner', () => {
  it('uses the nano horizontal spinner when the circular SVG component is unavailable', () => {
    const { container } = render(<Spinner className="test-spinner" color="#123456" mode="circular" size="large" />);
    const spinner = container.firstElementChild;

    expect(spinner).toHaveClass('load', 'nano', 'test-spinner');
    expect(container.querySelectorAll('.bar')).toHaveLength(1);
  });
});
