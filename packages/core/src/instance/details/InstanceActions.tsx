import React from 'react';
import { Dropdown } from 'react-bootstrap';

export interface Action {
  label: string;
  triggerAction: () => void;
}

export interface IInstanceActionsProps {
  actions: Action[];
  title?: string;
}

export const InstanceActions = ({ actions, title }: IInstanceActionsProps) => (
  <div style={{ display: 'inline-block' }}>
    <Dropdown className="dropdown" id="instace-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
        <span>{title || 'Instance Actions'}</span>
      </Dropdown.Toggle>
      <Dropdown.Menu>
        {(actions || []).map((action) => (
          <li key={`action-${action.label}`} id={`instance-action-${action.label}`}>
            <a onClick={action.triggerAction}>{action.label}</a>
          </li>
        ))}
      </Dropdown.Menu>
    </Dropdown>
  </div>
);
