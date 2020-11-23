import React, { memo } from 'react';
import ReactGA from 'react-ga';
import { DateTime } from 'luxon';
import * as distanceInWords from 'date-fns/distance_in_words';

import { IManagedArtifactVersionLifecycleStep } from '../domain';
import { Application } from '../application';

import { StatusCard } from './StatusCard';
import { Button } from './Button';

const SUPPORTED_TYPES = ['BAKE'];

const cardConfigurationByType = {
  BAKE: {
    iconName: 'bake',
    appearance: {
      NOT_STARTED: 'future',
      RUNNING: 'info',
      SUCCEEDED: 'neutral',
      FAILED: 'error',
    },
    title: ({ status, startedAt, completedAt }: IManagedArtifactVersionLifecycleStep) => {
      const startedAtDate = startedAt ? DateTime.fromISO(startedAt).toJSDate() : null;
      const completedAtDate = completedAt ? DateTime.fromISO(completedAt).toJSDate() : null;

      switch (status) {
        case 'NOT_STARTED':
          return 'An image will be baked before deployment';
        case 'RUNNING':
          return 'Baking';
        case 'SUCCEEDED':
          return `Baked in ${distanceInWords(startedAtDate, completedAtDate)}`;
        case 'FAILED':
          return `Baking failed after ${distanceInWords(startedAtDate, completedAtDate)}`;
        default:
          return null;
      }
    },
  },
} as const;

const logEvent = (label: string, application: string, reference: string, type: string, status: string) =>
  ReactGA.event({
    category: 'Environments - version details',
    action: label,
    label: `${application}:${reference}:${type}:${status}`,
  });

const getTimestamp = (startedAt: string, completedAt: string) => {
  if (completedAt) {
    return DateTime.fromISO(completedAt);
  } else if (startedAt) {
    return DateTime.fromISO(startedAt);
  } else {
    return null;
  }
};

export interface PreDeploymentStepCardProps {
  step: IManagedArtifactVersionLifecycleStep;
  application: Application;
  reference: string;
}

export const PreDeploymentStepCard = memo(({ step, application, reference }: PreDeploymentStepCardProps) => {
  const { type, status, startedAt, completedAt, link } = step;

  if (!SUPPORTED_TYPES.includes(type)) {
    return null;
  }

  const { iconName, appearance, title } = cardConfigurationByType[type];

  return (
    <StatusCard
      appearance={appearance[status]}
      active={status !== 'NOT_STARTED'}
      iconName={iconName}
      timestamp={getTimestamp(startedAt, completedAt)}
      title={title(step)}
      actions={
        link && (
          <Button
            onClick={() => {
              window.open(link, '_blank', 'noopener noreferrer');
              logEvent('Pre-deployment details link clicked', application.name, reference, type, status);
            }}
          >
            See {status === 'RUNNING' ? 'progress' : 'details'}
          </Button>
        )
      }
    />
  );
});
