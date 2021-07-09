import React from 'react';

import { IconNames } from '@spinnaker/presentation';
import { Application } from '../application';
import { IManagedResourceSummary, ManagedResourceStatus } from '../domain';
import { logger } from '../utils';

interface IViewConfiguration {
  appearance: 'info' | 'warning' | 'error';
  iconName: IconNames;
  popoverContents: (resourceSummary: IManagedResourceSummary, application?: Application) => JSX.Element;
}

const logClick = (label: string, resourceId: string, status: ManagedResourceStatus) =>
  logger.log({
    category: 'Managed Resource Status Indicator',
    action: `${label} clicked`,
    data: { label: `${resourceId},${status}` },
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
          <b>Spinnaker is taking action to resolve a difference from this resource's desired state.</b>
        </p>
        <p>
          You can click History to see more. <LearnMoreLink resourceSummary={resourceSummary} />
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
          <b>Spinnaker has started managing this resource.</b>
        </p>
        <p>
          If a difference from the desired state is detected, Spinnaker will act to correct it.{' '}
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
          <b>Spinnaker detected a difference from the desired state.</b>
        </p>
        <p>
          In a moment, Spinnaker will take action to bring this resource back to its desired state. You can click
          History to see more. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  DIFF_NOT_ACTIONABLE: {
    appearance: 'warning',
    iconName: 'mdDiff',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Spinnaker detected a difference from the desired state, but can't take action to resolve it.</b>
        </p>
        <p>
          Spinnaker doesn't have a way of resolving this kind of difference. Manual action might be required. You can
          click History to see more. <LearnMoreLink resourceSummary={resourceSummary} />
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
          Something required for management is temporarily experiencing issues. Spinnaker can't take action right now,
          but should be able to soon. You can click History to see more.{' '}
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
          Something required for management isn't ready yet. Spinnaker can't take action right now, but will resume once
          the missing dependencies exist. You can click History to see more.{' '}
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
          Something went wrong while trying to check this resource's current state. Spinnaker can't take action right
          now, and manual intervention might be required. You can click History to see more.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
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
          <b>Spinnaker is managing this resource.</b>
        </p>
        <p>
          If a difference from the desired state is detected, Spinnaker will act to correct it.{' '}
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
          <b>Management is paused.</b>
        </p>
        {application.isManagementPaused && (
          <p>
            Spinnaker is configured to manage this resource, but management for the entire application is temporarily
            paused. <LearnMoreLink resourceSummary={resourceSummary} />
          </p>
        )}
        {!application.isManagementPaused && (
          <p>
            Spinnaker is configured to manage this resource, but management has been temporarily paused.{' '}
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
          <b>Management was just resumed.</b>
        </p>
        <p>
          Management was resumed after being temporarily paused. If Spinnaker detects that a difference from the desired
          state occurred while paused, it will act to correct it. <LearnMoreLink resourceSummary={resourceSummary} />
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
          <b>Spinnaker detected a difference from the desired state, but hasn't been able to correct it.</b>
        </p>
        <p>
          Spinnaker has been trying to correct a difference, but taking action hasn't helped. Manual intervention might
          be required. You can click History to see more. <LearnMoreLink resourceSummary={resourceSummary} />
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
          Spinnaker is configured to manage this resource, but can't calculate its current status.{' '}
          <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
  WAITING: {
    appearance: 'info',
    iconName: 'mdCreated',
    popoverContents: (resourceSummary: IManagedResourceSummary) => (
      <>
        <p>
          <b>Waiting for information.</b>
        </p>
        <p>
          This resource is part of a brand new environment. Spinnaker is waiting for an artifact to become available to
          deploy. It normally takes less than 5 minutes for a newly created artifact to be seen, and after that all
          constraints need to pass in order for it to start deploying. You can click History to see a more detailed
          message. <LearnMoreLink resourceSummary={resourceSummary} />
        </p>
      </>
    ),
  },
};
