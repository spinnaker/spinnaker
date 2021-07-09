import { UISref } from '@uirouter/react';
import React from 'react';
import { Dropdown, Glyphicon, MenuItem } from 'react-bootstrap';

import { SETTINGS } from '../config';

import './HelpMenu.less';

const DOCS_URL = 'https://spinnaker.io/docs';
const COMMUNITY_URL = 'https://spinnaker.io/community';

const Feedback = () =>
  SETTINGS.feedback && SETTINGS.feedback.url ? (
    <MenuItem href={SETTINGS.feedback.url} target="_blank">
      <i className={SETTINGS.feedback.icon || 'fa fa-envelope'} />
      &nbsp; {SETTINGS.feedback.text || 'Send feedback'}
    </MenuItem>
  ) : null;

const AdditionalHelpLinks = () =>
  SETTINGS.additionalHelpLinks && SETTINGS.additionalHelpLinks.length ? (
    <>
      {SETTINGS.additionalHelpLinks.map((helpLink, i) => (
        <MenuItem href={helpLink.url} key={i} target="_blank">
          {helpLink.icon ? (
            <span>
              <i className={helpLink.icon} /> &nbsp;
            </span>
          ) : null}
          {helpLink.text || `Additional Help`}
        </MenuItem>
      ))}
    </>
  ) : null;

const Version = () => {
  if (!SETTINGS.version) {
    return null;
  }

  const CHANGELOG_PATH = SETTINGS.version.replace(/\./g, '-');
  const CHANGELOG_URL = SETTINGS.changelogUrl.replace(':version', CHANGELOG_PATH);

  return (
    <MenuItem href={CHANGELOG_URL} target="_blank">
      Spinnaker {SETTINGS.version}
    </MenuItem>
  );
};

export const HelpMenu = () => {
  return (
    <li className="help-menu">
      <Dropdown id="help-menu-dropdown" pullRight={true}>
        <Dropdown.Toggle className="hidden-lg" noCaret={true}>
          <Glyphicon glyph="question-sign" />
        </Dropdown.Toggle>
        <Dropdown.Menu>
          <Feedback />
          <AdditionalHelpLinks />
          <MenuItem href={DOCS_URL} target="_blank">
            Docs
          </MenuItem>
          <MenuItem divider={true} />
          <MenuItem href={COMMUNITY_URL} target="_blank">
            Community Resources
          </MenuItem>
          <Version />
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
        <Dropdown.Toggle className="hidden-xs hidden-sm hidden-md" noCaret={true}>
          <Glyphicon glyph="question-sign" /> Help
        </Dropdown.Toggle>
        <Dropdown.Menu>
          <Feedback />
          <AdditionalHelpLinks />
          <MenuItem href={DOCS_URL} target="_blank">
            Docs
          </MenuItem>
          <MenuItem href={COMMUNITY_URL} target="_blank">
            Community Resources
          </MenuItem>
          <Version />
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
