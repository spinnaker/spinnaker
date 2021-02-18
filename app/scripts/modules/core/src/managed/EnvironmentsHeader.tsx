import React from 'react';
import { Dropdown } from 'react-bootstrap';

import { Illustration, IllustrationName } from '@spinnaker/presentation';

import { showToggleManagedResourceModal } from './ToggleManagedResourceForApplication';
import { Application } from '../application';

import './EnvironmentsHeader.less';

interface IEnvironmentsHeaderProps {
  app: Application;
  resourceInfo: { managed: number; total: number };
}

export const ToggleManagedResourceAction = ({ app }: { app: Application }) => (
  <li>
    <a
      onClick={() => {
        showToggleManagedResourceModal({ application: app });
      }}
    >
      {app.isManagementPaused ? 'Resume management' : 'Disable management'}
    </a>
  </li>
);

const environmentHeaderInfo: {
  [key: string]: { description: string | null; icon: IllustrationName; title: (info: string) => string };
} = {
  running: {
    description: null,
    icon: 'runManagement',
    title: (info: string) => `Spinnaker is managing ${info} resources.`,
  },
  paused: {
    description: 'Any actions made will take effect once management is resumed.',
    icon: 'disableManagement',
    title: () => 'Management is disabled for this application.',
  },
};

export const EnvironmentsHeader = ({ app, resourceInfo: { managed, total } }: IEnvironmentsHeaderProps) => {
  const { description, icon, title } = environmentHeaderInfo[app.isManagementPaused ? 'paused' : 'running'];

  return (
    <div className="EnvironmentsHeader">
      <div className="flex-container-h sp-padding-m sp-padding-l-left">
        <div style={{ width: 115, marginTop: -6 }}>
          <Illustration name={icon} />
        </div>
        <div className="flex-container-v middle sp-margin-xl-left">
          <div className="heading-3 text-bold">{title(managed === total ? `${total}` : `${managed}/${total}`)}</div>
          {description && <div className="sp-margin-s-top">{description}</div>}
          <Dropdown id="application-actions" className="sp-margin-l-top">
            <Dropdown.Toggle className="dropdown-toggle-btn">
              <span className="text-bold">Application Actions</span>
            </Dropdown.Toggle>
            <Dropdown.Menu className="dropdown-menu">
              <ToggleManagedResourceAction app={app} />
            </Dropdown.Menu>
          </Dropdown>
        </div>
      </div>
    </div>
  );
};
