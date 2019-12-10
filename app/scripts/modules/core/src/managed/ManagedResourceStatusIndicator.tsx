import React from 'react';
import ReactGA from 'react-ga';
import classNames from 'classnames';
import { UISref } from '@uirouter/react';

import { HoverablePopover } from 'core/presentation';
import { IManagedResourceSummary, ManagedResourceStatus } from 'core/domain';

import './ManagedResourceStatusIndicator.less';

const viewConfigurationByStatus = {
  ACTUATING: {
    iconClass: 'icon-md-actuating',
    colorClass: 'info',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Action is being taken to resolve a drift from the declarative configuration.</b>
        </p>
        <p>
          Check the{' '}
          <UISref to="home.applications.application.tasks">
            <a>tasks view</a>
          </UISref>{' '}
          to see work that's in progress.
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.ACTUATING} />
      </>
    ),
  },
  CREATED: {
    iconClass: 'icon-md-created',
    colorClass: 'info',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Spinnaker has started continuously managing this resource.</b>
        </p>
        <p>
          If its actual configuration drifts from the declarative configuration, Spinnaker will automatically correct
          it. Changes made in the UI will be stomped in favor of the declarative configuration.
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.CREATED} />
      </>
    ),
  },
  DIFF: {
    iconClass: 'icon-md-diff',
    colorClass: 'info',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>A drift from the declarative configuration was detected.</b>
        </p>
        <p>Spinnaker will automatically take action to bring this resource back to its desired state.</p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.DIFF} />
      </>
    ),
  },
  ERROR: {
    iconClass: 'icon-md-error',
    colorClass: 'error',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Something went wrong.</b>
        </p>
        <p>
          Spinnaker is configured to continuously manage this resource, but something went wrong trying to check its
          current state. Automatic action can't be taken right now, and manual intervention might be required.
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.ERROR} />
      </>
    ),
  },
  HAPPY: {
    iconClass: 'icon-md',
    colorClass: 'info',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Spinnaker is continuously managing this resource.</b>
        </p>
        <p>Changes made in the UI will be stomped in favor of the declarative configuration.</p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.HAPPY} />
      </>
    ),
  },
  PAUSED: {
    iconClass: 'icon-md-paused',
    colorClass: 'warning',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Continuous management is paused.</b>
        </p>
        <p>
          Spinnaker is configured to continuously manage this resource, but management is temporarily paused. You can
          resume management on the{' '}
          <UISref to="home.applications.application.config" params={{ section: 'managed-resources' }}>
            <a>config view</a>
          </UISref>
          .
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.PAUSED} />
      </>
    ),
  },
  RESUMED: {
    iconClass: 'icon-md-resumed',
    colorClass: 'info',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Continuous management was just resumed.</b>
        </p>
        <p>
          Management was resumed after being temporarily paused. If Spinnaker detects that a drift from the declarative
          configuration occurred while paused, it will take automatic action to resolve the drift.
        </p>
        <p>
          You can pause and resume management on the{' '}
          <UISref to="home.applications.application.config" params={{ section: 'managed-resources' }}>
            <a>config view</a>
          </UISref>
          .
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.RESUMED} />
      </>
    ),
  },
  UNHAPPY: {
    iconClass: 'icon-md-flapping',
    colorClass: 'error',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>A drift from the declarative configuration was detected, but Spinnaker hasn't been able to correct it.</b>
        </p>
        <p>
          Spinnaker has been trying to correct a detected drift, but taking automatic action hasn't helped. Manual
          intervention might be required.
        </p>
        <p>
          You can temporarily pause management on the{' '}
          <UISref to="home.applications.application.config" params={{ section: 'managed-resources' }}>
            <a>config view</a>
          </UISref>{' '}
          to troubleshoot or make manual changes.
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.UNHAPPY} />
      </>
    ),
  },
  UNKNOWN: {
    iconClass: 'icon-md-unknown',
    colorClass: 'warning',
    popoverContents: (id: string) => (
      <>
        <p>
          <b>Unable to determine resource status.</b>
        </p>
        <p>
          Spinnaker is configured to continuously manage this resource, but its current status can't be calculated right
          now.
        </p>
        <LearnMoreLink id={id} status={ManagedResourceStatus.UNKNOWN} />
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

const LearnMoreLink = ({ id, status }: { id: string; status: ManagedResourceStatus }) => (
  <p className="sp-margin-m-top sp-margin-xs-bottom">
    <a
      target="_blank"
      onClick={() => logClick('Status docs link', id, status)}
      href={`https://www.spinnaker.io/reference/managed-delivery/resource-status/#${status.toLowerCase()}`}
    >
      Learn more about this
    </a>
  </p>
);

export interface IManagedResourceStatusIndicatorProps {
  shape: 'square' | 'circle';
  resourceSummary: IManagedResourceSummary;
}

export const ManagedResourceStatusIndicator = ({
  shape,
  resourceSummary: { id, status },
}: IManagedResourceStatusIndicatorProps) => {
  return (
    <div className="flex-container-h stretch ManagedResourceStatusIndicator">
      <HoverablePopover template={viewConfigurationByStatus[status].popoverContents(id)} placement="left">
        <div className={classNames('flex-container-h middle', shape, viewConfigurationByStatus[status].colorClass)}>
          <i className={classNames('fa', viewConfigurationByStatus[status].iconClass)} />
        </div>
      </HoverablePopover>
    </div>
  );
};
