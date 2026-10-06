import { render, screen } from '@testing-library/react';
import React from 'react';

import { StyleguideRoute } from './StyleguideRoute';

describe('<StyleguideRoute />', () => {
  it('StyleguideRoute renders the styleguide iframe', () => {
    render(<StyleguideRoute />);
    const iframe = screen.getByTitle('Spinnaker styleguide');

    expect(iframe).toHaveAttribute('src', '/styleguide.html');
    expect(iframe.style.border).toBe('0px');
    expect(iframe.style.height).toBe('calc(100vh - 60px)');
    expect(iframe.style.width).toBe('100%');
  });
});
