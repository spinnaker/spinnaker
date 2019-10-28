import * as React from 'react';
import * as ReactGA from 'react-ga';
import { Dropdown } from 'react-bootstrap';

import { SETTINGS } from 'core/config/settings';
import { HoverablePopover } from 'core/presentation';

import { IManagedResourceSummary } from './ManagedReader';

import './ManagedResourceDetailsIndicator.less';

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
    <div className="vertical middle center band band-info ManagedResourceDetailsIndicator">
      <HoverablePopover template={helpText} placement="left">
        <span className="summary-message horizontal sp-margin-s-bottom">
          <span className="rainbow-icon">🌈</span>
          Managed by Spinnaker
        </span>
      </HoverablePopover>
      <Dropdown className="dropdown" id="server-group-managed-resource-dropdown" pullRight={true}>
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
  );
};
