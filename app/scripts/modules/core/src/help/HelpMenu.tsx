import * as React from 'react';
import { Dropdown, MenuItem, Glyphicon } from 'react-bootstrap';
import { UISref } from '@uirouter/react';

import './HelpMenu.less';

import { SETTINGS } from '@spinnaker/core';

const DOCS_URL = 'https://spinnaker.io/docs';
const COMMUNITY_URL = 'https://spinnaker.io/community';

export const HelpMenu = () => {
  return (
    <li className="help-menu">
      <Dropdown id="help-menu-dropdown" pullRight={true}>
        <Dropdown.Toggle className="hidden-lg" noCaret={true}>
          <Glyphicon glyph="question-sign" />
        </Dropdown.Toggle>
        <Dropdown.Menu>
          <MenuItem href={DOCS_URL} target="_blank">
            Docs
          </MenuItem>
          <MenuItem divider={true} />
          <MenuItem href={COMMUNITY_URL} target="_blank">
            Community Resources
          </MenuItem>
          {SETTINGS.feature.pagerDuty && (
            <li role="presentation">
              <UISref to="home.page">
                <a className="clickable">
                  <span className="feedback-item-label">Send a Page</span>
                </a>
              </UISref>
            </li>
          )}
        </Dropdown.Menu>
      </Dropdown>

      <Dropdown id="help-menu-dropdown-large" pullRight={true}>
        <Dropdown.Toggle className="hidden-xs hidden-sm hidden-md">Help</Dropdown.Toggle>
        <Dropdown.Menu>
          <MenuItem href={DOCS_URL} target="_blank">
            Docs
          </MenuItem>
          <MenuItem href={COMMUNITY_URL} target="_blank">
            Community Resources
          </MenuItem>
          {SETTINGS.feature.pagerDuty && (
            <li role="presentation">
              <UISref to="home.page">
                <a className="clickable">
                  <span className="feedback-item-label">Send a Page</span>
                </a>
              </UISref>
            </li>
          )}
        </Dropdown.Menu>
      </Dropdown>
    </li>
  );
};
