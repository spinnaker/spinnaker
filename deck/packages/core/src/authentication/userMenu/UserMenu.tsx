import * as React from 'react';
import { Dropdown } from 'react-bootstrap';

import { AuthenticationInitializer } from '../AuthenticationInitializer';
import { AuthenticationService } from '../AuthenticationService';
import { SETTINGS } from '../../config/settings';

import './userMenu.less';

export const UserMenu = () => {
  const authenticatedUser = AuthenticationService.getAuthenticatedUser();
  const showLogOutDropdown = authenticatedUser.authenticated;

  if (!SETTINGS.authEnabled) {
    return null;
  }

  return (
    <div className="user-menu">
      <Dropdown id="user-menu-dropdown">
        <Dropdown.Toggle>
          <i className="hidden-lg glyphicon glyphicon-user" />
          <span className="hidden-xs hidden-sm hidden-md">{authenticatedUser.name}</span>
        </Dropdown.Toggle>
        {showLogOutDropdown && (
          <Dropdown.Menu>
            <li className="sp-padding-xs" onClick={() => AuthenticationInitializer.logOut()}>
              Log Out
            </li>
          </Dropdown.Menu>
        )}
      </Dropdown>
    </div>
  );
};
