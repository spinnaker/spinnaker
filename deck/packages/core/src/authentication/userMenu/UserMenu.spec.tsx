import React from 'react';
import { mount } from 'enzyme';

import { SETTINGS } from '../../config/settings';
import { AuthenticationService } from '../AuthenticationService';
import { UserMenu } from './UserMenu';

describe('UserMenu', () => {
  beforeEach(() => {
    SETTINGS.resetToOriginal();
    SETTINGS.authEnabled = true;
    AuthenticationService.reset();
  });

  afterEach(() => SETTINGS.resetToOriginal());

  it('does not render a dropdown while the user is unauthenticated', () => {
    expect(() => mount(<UserMenu />)).not.toThrow();
  });
});
