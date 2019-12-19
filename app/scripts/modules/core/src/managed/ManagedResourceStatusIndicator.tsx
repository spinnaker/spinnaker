import React from 'react';
import ReactGA from 'react-ga';
import classNames from 'classnames';
import { UISref } from '@uirouter/react';

import { HoverablePopover, IHoverablePopoverContentsProps } from 'core/presentation';
import { IManagedResourceSummary, ManagedResourceStatus } from 'core/domain';
import { Application } from 'core/application';

import { toggleResourcePause } from './toggleResourceManagement';

import './ManagedResourceStatusIndicator.less';

interface IViewConfiguration {
  iconClass: string;
  colorClass: string;
  popoverContents: (resourceSummary: IManagedResourceSummary, application?: Application) => JSX.Element;
}

const viewConfigurationByStatus: { [status in ManagedResourceStatus]: IViewConfiguration } = {
  ACTUATING: {
    iconClass: 'icon-md-actuating',
    colorClass: 'info',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Action is being taken to resolve a drift from the declarative configuration.</b>
        </p>
        <p>
          Check the{' '}
          <UISref to="home.applications.application.tasks">
            <a>tasks view</a>
          </UISref>{' '}
          to see work that's in progress. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  CREATED: {
    iconClass: 'icon-md-created',
    colorClass: 'info',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Spinnaker has started continuously managing this resource.</b>
        </p>
        <p>
          If its actual configuration drifts from the declarative configuration, Spinnaker will automatically correct
          it. Changes made in the UI will be stomped in favor of the declarative configuration.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  DIFF: {
    iconClass: 'icon-md-diff',
    colorClass: 'info',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>A drift from the declarative configuration was detected.</b>
        </p>
        <p>
          Spinnaker will automatically take action to bring this resource back to its desired state.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  ERROR: {
    iconClass: 'icon-md-error',
    colorClass: 'error',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Something went wrong.</b>
        </p>
        <p>
          Spinnaker is configured to continuously manage this resource, but something went wrong trying to check its
          current state. Automatic action can't be taken right now, and manual intervention might be required.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  HAPPY: {
    iconClass: 'icon-md',
    colorClass: 'info',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Spinnaker is continuously managing this resource.</b>
        </p>
        <p>
          Changes made in the UI will be stomped in favor of the declarative configuration.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  PAUSED: {
    iconClass: 'icon-md-paused',
    colorClass: 'warning',
    popoverContents: (resourceSummary: IManagedResourceSummary, application: Application) => (
      <>
        <p>
          <b>Continuous management is paused.</b>
        </p>
        {application.isManagementPaused && (
          <p>
            Spinnaker is configured to continuously manage this resource, but management for the entire application is
            temporarily paused. <LearnMoreLink resourceSummary={resourceSummary} />
          </p>
        )}
        {!application.isManagementPaused && (
          <p>
            Spinnaker is configured to continuously manage this resource, but management has been temporarily paused.{' '}
            <LearnMoreLink resourceSummary={resourceSummary} />
          </p>
        )}
      </>
    ),
  },
  RESUMED: {
    iconClass: 'icon-md-resumed',
    colorClass: 'info',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Continuous management was just resumed.</b>
        </p>
        <p>
          Management was resumed after being temporarily paused. If Spinnaker detects that a drift from the declarative
          configuration occurred while paused, it will take automatic action to resolve the drift.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  UNHAPPY: {
    iconClass: 'icon-md-flapping',
    colorClass: 'error',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>A drift from the declarative configuration was detected, but Spinnaker hasn't been able to correct it.</b>
        </p>
        <p>
          Spinnaker has been trying to correct a detected drift, but taking automatic action hasn't helped. Manual
          intervention might be required. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  UNKNOWN: {
    iconClass: 'icon-md-unknown',
    colorClass: 'warning',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Unable to determine resource status.</b>
        </p>
        <p>
          Spinnaker is configured to continuously manage this resource, but its current status can't be calculated right
          now. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
};

const logClick = (label: string, resourceId: string, status: ManagedResourceStatus) =>
  ReactGA.event({
    category: 'Managed Resource Status Indicator',
    action: `${label} clicked`,
    label: `${resourceId},${status}`,
  });

const LearnMoreLink = ({ resourceSummary }: { resourceSummary: IManagedResourceSummary }) => (
  <a
    target="_blank"
    onClick={() => logClick('Status docs link', resourceSummary.id, resourceSummary.status)}
    href={`https://www.spinnaker.io/reference/managed-delivery/resource-status/#${resourceSummary.status.toLowerCase()}`}
  >
    Learn more
  </a>
);

const PopoverLinks = ({
  resourceSummary,
  application,
  hidePopover,
}: {
  resourceSummary: IManagedResourceSummary;
  application: Application;
  hidePopover: () => void;
}) => (
  <div className="horizontal right">
    {!resourceSummary.isPaused && (
      <p className="sp-margin-m-top sp-margin-xs-bottom">
        <button className="passive" onClick={() => toggleResourcePause(resourceSummary, application, hidePopover)}>
          <i className="fa fa-pause" /> Pause management of this resource
        </button>
      </p>
    )}
    {resourceSummary.isPaused && !application.isManagementPaused && (
      <p className="sp-margin-m-top sp-margin-xs-bottom">
        <button className="passive" onClick={() => toggleResourcePause(resourceSummary, application, hidePopover)}>
          <i className="fa fa-play" /> Resume management of this resource
        </button>
      </p>
    )}
    {application.isManagementPaused && (
      <p>
        <UISref to="home.applications.application.config" params={{ section: 'managed-resources' }}>
          <a>Resume application management</a>
        </UISref>
      </p>
    )}
  </div>
);

export interface IManagedResourceStatusIndicatorProps {
  shape: 'square' | 'circle';
  resourceSummary: IManagedResourceSummary;
  application: Application;
}

export const ManagedResourceStatusIndicator = ({
  shape,
  resourceSummary,
  application,
}: IManagedResourceStatusIndicatorProps) => {
  const { status } = resourceSummary;
  const PopoverContents = ({ hidePopover }: IHoverablePopoverContentsProps) => (
    <>
      {viewConfigurationByStatus[status].popoverContents(resourceSummary, application)}
      <PopoverLinks resourceSummary={resourceSummary} application={application} hidePopover={hidePopover} />
    </>
  );
  return (
    <div className="flex-container-h stretch ManagedResourceStatusIndicator">
      <HoverablePopover Component={PopoverContents} placement="left">
        <div className={classNames('flex-container-h middle', shape, viewConfigurationByStatus[status].colorClass)}>
          <i className={classNames('fa', viewConfigurationByStatus[status].iconClass)} />
        </div>
      </HoverablePopover>
    </div>
  );
};
