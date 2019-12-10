import React from 'react';
import ReactGA from 'react-ga';
import { Dropdown } from 'react-bootstrap';

import { SETTINGS } from 'core/config/settings';
import { HoverablePopover } from 'core/presentation';

import { IManagedResourceSummary } from 'core/domain';

import './ManagedResourceDetailsIndicator.css';

export interface IManagedResourceDetailsIndicatorProps {
  resourceSummary: IManagedResourceSummary;
}

const logClick = (label: string, resourceId: string) =>
  ReactGA.event({
    category: 'Managed Resource Menu',
    action: `${label} clicked`,
    label: resourceId,
  });

export const ManagedResourceDetailsIndicator = ({ resourceSummary }: IManagedResourceDetailsIndicatorProps) => {
  if (!resourceSummary) {
    return null;
  }

  const { id } = resourceSummary;

  const helpText = (
    <>
      <p>
        <b>Spinnaker is continuously managing this resource.</b>
      </p>
      <p>
        Changes made in the UI will be stomped in favor of the existing declarative configuration.{' '}
        <a
          target="_blank"
          onClick={() => logClick('Learn More', id)}
          href="https://www.spinnaker.io/reference/managed-delivery"
        >
          Learn More
        </a>
      </p>
    </>
  );

  return (
    <div className="flex-container-h middle ManagedResourceDetailsIndicator">
      <HoverablePopover template={helpText} placement="left">
        <div className="md-logo flex-container-h middle">
          <img src={require('./icons/md-logo-color.svg')} width="36px" />
        </div>
      </HoverablePopover>
      <div className="flex-container-v middle flex-1 sp-margin-l-left">
        <span className="summary-message sp-margin-s-bottom">Managed by Spinnaker</span>
        <Dropdown
          className="resource-actions sp-margin-xs-bottom flex-pull-left"
          id="server-group-managed-resource-dropdown"
          pullRight={true}
        >
          <Dropdown.Toggle className="btn btn-sm btn-default dropdown-toggle">Resource Actions</Dropdown.Toggle>
          <Dropdown.Menu className="dropdown-menu">
            <li>
              <a target="_blank" onClick={() => logClick('History', id)} href={`${SETTINGS.gateUrl}/history/${id}`}>
                History
              </a>
            </li>
            <li>
              <a
                target="_blank"
                onClick={() => logClick('Raw Source', id)}
                href={`${SETTINGS.gateUrl}/managed/resources/${id}`}
              >
                Raw Source
              </a>
            </li>
          </Dropdown.Menu>
        </Dropdown>
      </div>
    </div>
  );
};
