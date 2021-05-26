import { DateTime } from 'luxon';
import React, { memo } from 'react';

import { Button } from '../Button';
import { StatusCard } from '../StatusCard';
import { Application } from '../../application';
import { IManagedArtifactVersionLifecycleStep } from '../../domain';
import { logger, timeDiffToString } from '../../utils';

const cardAppearanceByStatus = {
  NOT_STARTED: 'future',
  RUNNING: 'info',
  SUCCEEDED: 'neutral',
  FAILED: 'error',
  ABORTED: 'neutral',
  UNKNOWN: 'warning',
} as const;

const cardConfigurationByType = {
  BUILD: {
    iconName: 'build',
    title: ({ status, startedAt, completedAt }: IManagedArtifactVersionLifecycleStep) => {
      const startedAtDate = startedAt ? DateTime.fromISO(startedAt) : null;
      const completedAtDate = completedAt ? DateTime.fromISO(completedAt) : null;
      const timeDiff = startedAtDate && completedAtDate ? timeDiffToString(startedAtDate, completedAtDate) : undefined;

      switch (status) {
        case 'NOT_STARTED':
          return 'A build will run before deployment';
        case 'RUNNING':
          return 'Building';
        case 'SUCCEEDED':
          return `Built ${timeDiff ? `in ${timeDiff}` : ''}`;
        case 'FAILED':
          return `Build failed ${timeDiff ? `after ${timeDiff}` : ''}`;
        case 'ABORTED':
          return `Build aborted ${timeDiff ? `after ${timeDiff}` : ''}`;
        case 'UNKNOWN':
        default:
          return 'Unable to find the status of this build';
      }
    },
  },
  BAKE: {
    iconName: 'bake',
    title: ({ status, startedAt, completedAt }: IManagedArtifactVersionLifecycleStep) => {
      const startedAtDate = startedAt ? DateTime.fromISO(startedAt) : null;
      const completedAtDate = completedAt ? DateTime.fromISO(completedAt) : null;
      const timeDiff = startedAtDate && completedAtDate ? timeDiffToString(startedAtDate, completedAtDate) : undefined;

      switch (status) {
        case 'NOT_STARTED':
          return 'An image will be baked before deployment';
        case 'RUNNING':
          return 'Baking';
        case 'SUCCEEDED':
          return `Baked ${timeDiff ? `in ${timeDiff}` : ''}`;
        case 'FAILED':
          return `Baking failed ${timeDiff ? `after ${timeDiff}` : ''}`;
        case 'ABORTED':
          return `Baking aborted ${timeDiff ? `after ${timeDiff}` : ''}`;
        case 'UNKNOWN':
        default:
          return 'Unable to find the status of this bake';
      }
    },
  },
} as const;

const logEvent = (label: string, application: string, reference: string, type: string, status: string) =>
  logger.log({
    category: 'Environments - version details',
    action: label,
    data: { label: `${application}:${reference}:${type}:${status}` },
  });

const getTimestamp = (startedAt?: string, completedAt?: string) => {
  if (completedAt) {
    return DateTime.fromISO(completedAt);
  } else if (startedAt) {
    return DateTime.fromISO(startedAt);
  } else {
    return undefined;
  }
};

export interface PreDeploymentStepCardProps {
  step: IManagedArtifactVersionLifecycleStep;
  application: Application;
  reference: string;
}

export const PreDeploymentStepCard = memo(({ step, application, reference }: PreDeploymentStepCardProps) => {
  const { type, status, startedAt, completedAt, link } = step;

  const { iconName, title } = cardConfigurationByType[type];

  return (
    <StatusCard
      appearance={cardAppearanceByStatus[status]}
      active={status !== 'NOT_STARTED'}
      iconName={iconName}
      timestamp={getTimestamp(startedAt, completedAt)}
      title={title(step)}
      actions={
        link && (
          <a
            className="nostyle"
            href={link}
            rel="noopener noreferrer"
            onClick={() => {
              logEvent('Pre-deployment details link clicked', application.name, reference, type, status);
            }}
          >
            <Button>See {status === 'RUNNING' ? 'progress' : 'details'}</Button>
          </a>
        )
      }
    />
  );
});
