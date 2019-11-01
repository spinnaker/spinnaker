import * as React from 'react';
import * as ReactGA from 'react-ga';
import * as classNames from 'classnames';
import { UISref } from '@uirouter/react';

import { HoverablePopover } from 'core/presentation';
import { IManagedResourceSummary } from 'core/managed';

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
          to see work that's in progress. <LearnMoreLink id={id} />
        </p>
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
          it. Changes made in the UI will be stomped in favor of the declarative configuration.{' '}
          <LearnMoreLink id={id} />
        </p>
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
        <p>
          Spinnaker will automatically take action to bring this resource back to its desired state.{' '}
          <LearnMoreLink id={id} />
        </p>
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
          current state. Automatic action can't be taken right now, and manual intervention might be required.{' '}
          <LearnMoreLink id={id} />
        </p>
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
        <p>
          Changes made in the UI will be stomped in favor of the declarative configuration. <LearnMoreLink id={id} />
        </p>
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
          . <LearnMoreLink id={id} />
        </p>
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
          to troubleshoot or make manual changes. <LearnMoreLink id={id} />
        </p>
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
          now. <LearnMoreLink id={id} />
        </p>
      </>
    ),
  },
};

const logClick = (label: string, resourceId: string) =>
  ReactGA.event({
    category: 'Managed Resource Status Indicator',
    action: `${label} clicked`,
    label: resourceId,
  });

const LearnMoreLink = ({ id }: { id: string }) => (
  <a
    target="_blank"
    onClick={() => logClick('Learn More', id)}
    href="https://www.spinnaker.io/reference/managed-delivery"
  >
    Learn More
  </a>
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
    <HoverablePopover
      template={viewConfigurationByStatus[status].popoverContents(id)}
      placement="left"
      style={{ display: 'flex' }}
      wrapperClassName={classNames(
        'flex-container-h middle ManagedResourceStatusIndicator',
        shape,
        viewConfigurationByStatus[status].colorClass,
      )}
    >
      <i className={classNames('fa', viewConfigurationByStatus[status].iconClass)} />
    </HoverablePopover>
  );
};
