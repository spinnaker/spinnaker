import { render, screen } from '@testing-library/react';
import React from 'react';

import { AuthenticationService } from '../AuthenticationService';
import { UserMenu } from './UserMenu';
import { SETTINGS } from '../../config/settings';

describe('UserMenu', () => {
  beforeEach(() => {
    SETTINGS.resetToOriginal();
    SETTINGS.authEnabled = true;
    AuthenticationService.reset();
  });

  afterEach(() => SETTINGS.resetToOriginal());

  it('does not render a dropdown while the user is unauthenticated', () => {
    let container: HTMLElement;
    expect(() => ({ container } = render(<UserMenu />))).not.toThrow();
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
