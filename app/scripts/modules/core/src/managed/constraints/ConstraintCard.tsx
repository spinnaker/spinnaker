import classNames from 'classnames';
import React, { memo, useState } from 'react';

import { Button } from '../Button';
import { IUpdateConstraintStatusRequest, ManagedWriter } from '../ManagedWriter';
import { IStatusCardProps, StatusCard } from '../StatusCard';
import { Application, ApplicationDataSource } from '../../application';
import {
  ConstraintStatus,
  IConstraint,
  IManagedApplicationEnvironmentSummary,
  IManagedArtifactVersionEnvironment,
} from '../../domain';
import { IRequestStatus } from '../../presentation';
import { constraintsManager, hasSkippedConstraint } from './registry';
import { logger } from '../../utils';

import './ConstraintCard.less';

const constraintCardAppearanceByStatus: { [key in ConstraintStatus]: IStatusCardProps['appearance'] } = {
  NOT_EVALUATED: 'future',
  BLOCKED: 'future',
  PENDING: 'info',
  PASS: 'neutral',
  FAIL: 'error',
  OVERRIDE_PASS: 'neutral',
  OVERRIDE_FAIL: 'error',
  FORCE_PASS: 'neutral',
} as const;

const skippedConstraintCardAppearanceByStatus: { [key in ConstraintStatus]: IStatusCardProps['appearance'] } = {
  NOT_EVALUATED: 'future',
  BLOCKED: 'future',
  PENDING: 'future',
  PASS: 'neutral',
  FAIL: 'neutral',
  OVERRIDE_PASS: 'neutral',
  OVERRIDE_FAIL: 'neutral',
  FORCE_PASS: 'neutral',
} as const;

const logEvent = (label: string, application: string, environment: string, reference: string) =>
  logger.log({
    category: 'Environments - version details',
    action: label,
    data: { label: `${application}:${environment}:${reference}` },
  });

// TODO: improve this logic below
const overrideConstraintStatus = (
  application: Application,
  options: Omit<IUpdateConstraintStatusRequest, 'application'>,
) =>
  ManagedWriter.updateConstraintStatus({
    application: application.name,
    ...options,
  }).then(() => {
    const dataSource: ApplicationDataSource<IManagedApplicationEnvironmentSummary> = application.getDataSource(
      'environments',
    );

    // Here we wait to say things are fully done until we attempt to refresh, but don't
    // reject. If the refresh fails things are going to be in a bad state anyway,
    // so we don't want to wrongly imply that the override didn't take effect
    // just because we're unable check.
    return dataSource.refresh().catch(() => null);
  });

const getCardAppearance = (constraint: IConstraint, environment: IManagedArtifactVersionEnvironment) => {
  if (environment.state === 'skipped') {
    return skippedConstraintCardAppearanceByStatus[constraint.status];
  } else {
    return constraintCardAppearanceByStatus[constraint.status];
  }
};

export interface IConstraintCardProps {
  application: Application;
  environment: IManagedArtifactVersionEnvironment;
  reference: string;
  version: string;
  constraint: IConstraint;
}

export const ConstraintCard = memo(
  ({ application, environment, reference, version, constraint }: IConstraintCardProps) => {
    const { type } = constraint;

    const [actionStatus, setActionStatus] = useState<IRequestStatus>('NONE');

    const actions = constraintsManager.getActions(constraint, environment.state);

    if (!constraintsManager.isSupported(type)) {
      console.warn(
        new Error(`Unsupported constraint type ${type} — did you check for constraint support before rendering?`),
      );
    }

    const hasSkipped = hasSkippedConstraint(constraint, environment);

    return (
      <StatusCard
        appearance={getCardAppearance(constraint, environment)}
        active={environment.state !== 'skipped'}
        iconName={constraintsManager.getIcon(constraint)}
        timestamp={constraintsManager.getTimestamp(constraint, environment)}
        title={
          hasSkipped
            ? 'Environment was skipped before evaluating constraint'
            : constraintsManager.renderTitle(constraint)
        }
        description={!hasSkipped ? constraintsManager.renderDescription(constraint) : undefined}
        actions={
          actions && (
            <div
              className={classNames('flex-container-h middle', {
                'sp-group-margin-s-xaxis': actionStatus !== 'REJECTED',
              })}
            >
              {actionStatus !== 'REJECTED' &&
                actions.map(({ title, pass }) => {
                  return (
                    <Button
                      key={title + pass}
                      disabled={actionStatus === 'PENDING'}
                      onClick={() => {
                        setActionStatus('PENDING');
                        overrideConstraintStatus(application, {
                          environment: environment.name,
                          type,
                          reference,
                          version,
                          status: pass ? 'OVERRIDE_PASS' : 'OVERRIDE_FAIL',
                        })
                          .then(() => {
                            setActionStatus('RESOLVED');
                            logEvent(
                              `${type} constraint - ${title} action taken`,
                              application.name,
                              environment.name,
                              reference,
                            );
                          })
                          .catch(() => {
                            setActionStatus('REJECTED');
                            logEvent(
                              `${type} constraint - ${title} action failed`,
                              application.name,
                              environment.name,
                              reference,
                            );
                          });
                      }}
                    >
                      {title}
                    </Button>
                  );
                })}
              {actionStatus === 'REJECTED' && (
                <>
                  <span className="text-bold action-error-message sp-margin-l-right">Something went wrong</span>
                  <Button onClick={() => setActionStatus('NONE')}>Try again</Button>
                </>
              )}
            </div>
          )
        }
      />
    );
  },
);
