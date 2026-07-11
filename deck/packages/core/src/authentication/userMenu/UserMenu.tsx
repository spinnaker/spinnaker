import { UISref } from '@uirouter/react';
import * as React from 'react';
import { Dropdown } from 'react-bootstrap';

import { AuthenticationInitializer } from '../AuthenticationInitializer';
import { AuthenticationService } from '../AuthenticationService';
import { SETTINGS } from '../../config/settings';

import './userMenu.less';

export const UserMenu = () => {
  const authenticatedUser = AuthenticationService.getAuthenticatedUser();
  const showLogOutDropdown = authenticatedUser.authenticated;
  const canMintApiTokens = authenticatedUser.canMintApiTokens ?? false;

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
          <Dropdown.Menu pullRight>
            {canMintApiTokens && (
              <>
                <li role="presentation">
                  <UISref to="home.apiTokens">
                    <a role="menuitem">API Tokens</a>
                  </UISref>
                </li>
                <li role="presentation" className="divider" />
              </>
            )}
            <li role="presentation">
              <a role="menuitem" style={{ cursor: 'pointer' }} onClick={() => AuthenticationInitializer.logOut()}>
                Log Out
              </a>
            </li>
          </Dropdown.Menu>
        )}
      </Dropdown>
    </div>
  );
};
