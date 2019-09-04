import * as React from 'react';
import * as ReactGA from 'react-ga';
import { get, flatMap } from 'lodash';
import { Dropdown } from 'react-bootstrap';

import { SETTINGS } from 'core/config/settings';
import { IEntityTags } from 'core/domain';
import { HoverablePopover } from 'core/presentation';

import './ManagedResourceDetailsIndicator.less';

export const MANAGED_BY_SPINNAKER_TAG_NAME = 'spinnaker_ui_notice:managed_by_spinnaker';

export interface IManagedResourceDetailsIndicatorProps {
  entityTags: IEntityTags[];
}

const logClick = (label: string, resourceId: string) =>
  ReactGA.event({
    category: 'Managed Resource Menu',
    action: `${label} clicked`,
    label: resourceId,
  });

export const ManagedResourceDetailsIndicator = ({ entityTags }: IManagedResourceDetailsIndicatorProps) => {
  const managedTag =
    get(entityTags, 'length') &&
    flatMap(entityTags, ({ tags }) => tags).find(({ name }) => name === MANAGED_BY_SPINNAKER_TAG_NAME);

  if (!managedTag) {
    return null;
  }

  const {
    value: { keelResourceId },
  } = managedTag;

  const helpText = (
    <>
      <p>
        <b>Spinnaker is continuously managing this resource.</b>
      </p>
      <p>
        Changes made in the UI will be stomped in favor of the existing declarative configuration.{' '}
        <a
          target="_blank"
          onClick={() => logClick('Learn More', keelResourceId)}
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
            <a
              target="_blank"
              onClick={() => logClick('History', keelResourceId)}
              href={`${SETTINGS.gateUrl}/history/${keelResourceId}`}
            >
              History
            </a>
          </li>
          <li>
            <a
              target="_blank"
              onClick={() => logClick('Raw Source', keelResourceId)}
              href={`${SETTINGS.gateUrl}/managed/resources/${keelResourceId}`}
            >
              Raw Source
            </a>
          </li>
        </Dropdown.Menu>
      </Dropdown>
    </div>
  );
};
