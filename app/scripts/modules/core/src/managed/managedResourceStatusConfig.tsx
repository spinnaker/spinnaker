import React from 'react';
import ReactGA from 'react-ga';

import { Application } from 'core/application';
import { IManagedResourceSummary, ManagedResourceStatus } from 'core/domain';
import { IconNames } from 'core/presentation';

interface IViewConfiguration {
  appearance: 'info' | 'warning' | 'error';
  iconName: IconNames;
  popoverContents: (resourceSummary: IManagedResourceSummary, application?: Application) => JSX.Element;
}

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
    href={`https://www.spinnaker.io/guides/user/managed-delivery/resource-status/#${resourceSummary.status
      .toLowerCase()
      .replace('_', '-')}`}
  >
    Learn more
  </a>
);

export const viewConfigurationByStatus: { [status in ManagedResourceStatus]: IViewConfiguration } = {
  ACTUATING: {
    appearance: 'info',
    iconName: 'mdActuating',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Action is being taken to resolve a drift from the declarative configuration.</b>
        </p>
        <p>
          Check this resource's History to see details and track the work currently in progress.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  CREATED: {
    appearance: 'info',
    iconName: 'mdCreated',
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
    appearance: 'info',
    iconName: 'mdDiff',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>A drift from the declarative configuration was detected.</b>
        </p>
        <p>
          Spinnaker will automatically take action to bring this resource back to its desired state. Check the History
          to see details and track progress. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  CURRENTLY_UNRESOLVABLE: {
    appearance: 'warning',
    // Needs its own icon
    iconName: 'mdError',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Waiting for a temporary issue to pass.</b>
        </p>
        <p>
          Something required for management is temporarily experiencing issues. Automatic action can't be taken right
          now, but will likely resume soon. Check the History for details.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  MISSING_DEPENDENCY: {
    appearance: 'warning',
    // Needs its own icon
    iconName: 'mdError',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Waiting for a missing dependency to become available.</b>
        </p>
        <p>
          Something required for management isn't ready yet. Automatic action can't be taken right now, but will resume
          once the necessary dependencies exist. Check the History for details.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  ERROR: {
    appearance: 'error',
    iconName: 'mdError',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Something went wrong.</b>
        </p>
        <p>
          Spinnaker is configured to continuously manage this resource, but something went wrong trying to check its
          current state. Automatic action can't be taken right now, and manual intervention might be required. Check the
          History for details. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  HAPPY: {
    appearance: 'info',
    iconName: 'md',
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
    appearance: 'warning',
    iconName: 'mdPaused',
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
    appearance: 'info',
    iconName: 'mdResumed',
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
    appearance: 'error',
    iconName: 'mdUnhappy',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>A drift from the declarative configuration was detected, but Spinnaker hasn't been able to correct it.</b>
        </p>
        <p>
          Spinnaker has been trying to correct a detected drift, but taking automatic action hasn't helped. Manual
          intervention might be required. Check the History for details.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  UNKNOWN: {
    appearance: 'warning',
    iconName: 'mdUnknown',
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
