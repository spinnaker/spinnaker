import classnames from 'classnames';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import { ACTION_BUTTON_CLASS_NAME } from '../utils/defaults';
import { useLogEvent } from '../utils/logging';
import type { ICompareLinks } from '../versionMetadata/MetadataComponents';

export interface VersionAction {
  onClick?: () => void;
  href?: string;
  content: string;
  disabled?: boolean;
}

interface IArtifactActionsProps {
  actions?: VersionAction[];
  version: string;
  buildNumber?: string;
  compareLinks?: ICompareLinks;
  className?: string;
}

export const ArtifactActions = ({ version, buildNumber, actions, compareLinks, className }: IArtifactActionsProps) => {
  const compareActions: VersionAction[] = [
    { content: 'Current version', href: compareLinks?.current, disabled: !compareLinks?.current },
    { content: 'Previous version', href: compareLinks?.previous, disabled: !compareLinks?.previous },
  ];
  return (
    <div className={classnames('horizontal md-actions-gap', className)}>
      {actions?.map((action) => (
        <button
          key={action.content}
          className={ACTION_BUTTON_CLASS_NAME}
          disabled={action.disabled}
          onClick={action.onClick}
        >
          {action.content}
        </button>
      ))}
      <CompareToMenu id={`${version}-${buildNumber}-compare`} title="Compare to" actions={compareActions} />
    </div>
  );
};

export interface ICompareToMenuProps {
  id: string;
  actions: VersionAction[];
  title: string;
  className?: string;
  pullRight?: boolean;
}

const CompareToMenu = ({ id, title, actions, className, pullRight }: ICompareToMenuProps) => {
  const logEvent = useLogEvent('ArtifactActions');
  return (
    <Dropdown id={id} className={classnames('ArtifactActionsMenu', className)} pullRight={pullRight}>
      <Dropdown.Toggle className="md-btn md-action-button">{title}</Dropdown.Toggle>
      <Dropdown.Menu>
        {actions.map((action, index) => (
          <MenuItem
            key={index}
            disabled={action.disabled}
            onClick={() => {
              action.onClick?.();
              logEvent({ action: `Compare to - ${action.content}` });
            }}
            href={action.href}
            target="_blank"
          >
            {action.content}
          </MenuItem>
        ))}
      </Dropdown.Menu>
    </Dropdown>
  );
};
