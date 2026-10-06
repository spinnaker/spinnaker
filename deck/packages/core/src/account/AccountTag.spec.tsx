import { render } from '@testing-library/react';
import React from 'react';

import { AccountTag } from './AccountTag';

describe('AccountTag', () => {
  it('renders nothing for missing account values', () => {
    const { container } = render(<AccountTag account={null} />);

    expect(container).toBeEmptyDOMElement();
  });
});
