import { UISref } from '@uirouter/react';
import React, { ReactNode } from 'react';

import { Application } from '../application';
import { IManagedResourceSummary } from '../domain';
import { viewConfigurationByStatus } from './managedResourceStatusConfig';
import { HoverablePopover, IHoverablePopoverContentsProps } from '../presentation';
import { showManagedResourceHistoryModal } from './resourceHistory/ManagedResourceHistoryModal';
import { toggleResourcePause } from './toggleResourceManagement';

const PopoverActions = ({
  resourceSummary,
  application,
  hidePopover,
}: {
  resourceSummary: IManagedResourceSummary;
  application: Application;
  hidePopover?: () => void;
}) => {
  const historyButton = (
    <button
      className="passive flex-none"
      onClick={() => {
        hidePopover?.();
        showManagedResourceHistoryModal(resourceSummary);
      }}
    >
      <i className="fa fa-history" /> History
    </button>
  );
  return (
    <div className="horizontal right">
      <p className="flex-container-h middle sp-margin-m-top sp-margin-xs-bottom sp-group-margin-s-xaxis">
        {historyButton}
        {!resourceSummary.isPaused && (
          <button
            className="passive flex-none"
            onClick={() => toggleResourcePause(resourceSummary, application, hidePopover)}
          >
            <i className="fa fa-pause" /> Pause management of this resource
          </button>
        )}
        {resourceSummary.isPaused && !application.isManagementPaused && (
          <button
            className="passive flex-none"
            onClick={() => toggleResourcePause(resourceSummary, application, hidePopover)}
          >
            <i className="fa fa-play" /> Resume management of this resource
          </button>
        )}
        {application.isManagementPaused && (
          <UISref to="home.applications.application.config" params={{ section: 'managed-resources' }}>
            <a>Resume application management</a>
          </UISref>
        )}
      </p>
    </div>
  );
};

export interface IManagedResourceStatusPopover {
  application: Application;
  children: ReactNode;
  placement: 'left' | 'top' | 'bottom' | 'right';
  resourceSummary: IManagedResourceSummary;
}

export const ManagedResourceStatusPopover = ({
  application,
  children,
  placement,
  resourceSummary,
}: IManagedResourceStatusPopover) => {
  const { status } = resourceSummary;

  const PopoverContents = ({ hidePopover }: IHoverablePopoverContentsProps) => (
    <>
      {viewConfigurationByStatus[status].popoverContents(resourceSummary, application)}
      <PopoverActions resourceSummary={resourceSummary} application={application} hidePopover={hidePopover} />
    </>
  );
  return (
    <HoverablePopover Component={PopoverContents} delayHide={200} delayShow={200} placement={placement}>
      {children}
    </HoverablePopover>
  );
};
