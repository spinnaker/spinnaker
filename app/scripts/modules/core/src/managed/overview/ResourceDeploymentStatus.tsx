import React from 'react';

import { Icon } from '@spinnaker/presentation';

import { AnimatingPill, Pill } from '../Pill';
import { getArtifactVersionDisplayName } from '../displayNames';
import { IManagedArtifactSummary, IManagedEnvironmentSummary } from '../../domain';

export interface ResourceDeploymentStatusProps {
  environment?: string;
  artifactVersionsByState?: IManagedEnvironmentSummary['artifacts'][0]['versions'];
  artifactDetails?: IManagedArtifactSummary;
  showReferenceName?: boolean;
}

export const ResourceDeploymentStatus = ({
  environment,
  artifactVersionsByState,
  artifactDetails,
  showReferenceName,
}: ResourceDeploymentStatusProps) => {
  const current = artifactVersionsByState?.current
    ? artifactDetails?.versions.find(({ version }) => version === artifactVersionsByState?.current)
    : undefined;

  const deploying =
    artifactVersionsByState?.deploying &&
    artifactDetails?.versions.find(({ version }) => version === artifactVersionsByState?.deploying);

  const isCurrentVersionPinned = !!current?.environments.find(({ name }) => name === environment)?.pinned;
  const currentPill = current && (
    <Pill
      text={`${getArtifactVersionDisplayName(current)}${showReferenceName ? ' ' + artifactDetails?.reference : ''}`}
      bgColor={isCurrentVersionPinned ? 'var(--color-status-warning)' : undefined}
      textColor={isCurrentVersionPinned ? 'var(--color-icon-dark)' : undefined}
    />
  );
  const deployingPill = deploying && (
    <>
      <Icon appearance="neutral" name="caretRight" size="medium" />
      <AnimatingPill
        text={`${getArtifactVersionDisplayName(deploying)}${showReferenceName ? ' ' + artifactDetails?.reference : ''}`}
        textColor="var(--color-icon-neutral)"
      />
    </>
  );
  return (
    <>
      {currentPill}
      {deployingPill}
    </>
  );
};
