import React, { memo, useState } from 'react';
import classNames from 'classnames';

import {
  IStatefulConstraint,
  StatefulConstraintStatus,
  IStatelessConstraint,
  IManagedApplicationEnvironmentSummary,
} from '../../domain';
import { Application, ApplicationDataSource } from '../../application';
import { IRequestStatus } from '../../presentation';

import { StatusCard } from '../StatusCard';
import { ManagedWriter, IUpdateConstraintStatusRequest } from '../ManagedWriter';
import {
  isConstraintSupported,
  isConstraintStateful,
  getConstraintIcon,
  getConstraintSummary,
  getConstraintActions,
} from './constraintRegistry';

import './ConstraintCard.less';

const { NOT_EVALUATED, PENDING, PASS, FAIL, OVERRIDE_PASS, OVERRIDE_FAIL } = StatefulConstraintStatus;

const constraintCardAppearanceByStatus = {
  [NOT_EVALUATED]: 'inactive',
  [PENDING]: 'info',
  [PASS]: 'success',
  [FAIL]: 'error',
  [OVERRIDE_PASS]: 'success',
  [OVERRIDE_FAIL]: 'error',
} as const;

const logUnsupportedConstraintError = (type: string) => {
  console.error(
    new Error(`Unsupported constraint type ${type} — did you check for constraint support before rendering?`),
  );
};

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

const getCardAppearance = (constraint: IStatefulConstraint | IStatelessConstraint) => {
  if (isConstraintStateful(constraint)) {
    const { status } = constraint as IStatefulConstraint;
    return constraintCardAppearanceByStatus[status];
  } else {
    const { currentlyPassing } = constraint as IStatelessConstraint;
    return currentlyPassing ? 'success' : 'inactive';
  }
};

export interface IConstraintCardProps {
  application: Application;
  environment: string;
  version: string;
  constraint: IStatefulConstraint | IStatelessConstraint;
}

export const ConstraintCard = memo(({ application, environment, version, constraint }: IConstraintCardProps) => {
  const { type } = constraint;

  const [actionStatus, setActionStatus] = useState<IRequestStatus>('NONE');

  if (!isConstraintSupported(type)) {
    logUnsupportedConstraintError(type);
    return null;
  }

  const actions = getConstraintActions(constraint);

  return (
    <StatusCard
      appearance={getCardAppearance(constraint)}
      iconName={getConstraintIcon(type)}
      title={getConstraintSummary(constraint)}
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
                  <button
                    key={title + pass}
                    className="flex-container-h center middle text-bold constraint-override-action"
                    disabled={actionStatus === 'PENDING'}
                    onClick={() => {
                      setActionStatus('PENDING');
                      overrideConstraintStatus(application, {
                        environment,
                        type,
                        version,
                        status: pass ? OVERRIDE_PASS : OVERRIDE_FAIL,
                      })
                        .then(() => setActionStatus('RESOLVED'))
                        .catch(() => {
                          setActionStatus('REJECTED');
                        });
                    }}
                  >
                    {title}
                  </button>
                );
              })}
            {actionStatus === 'REJECTED' && (
              <>
                <span className="text-bold action-error-message sp-margin-l-right">Something went wrong</span>
                <button
                  className="flex-container-h center middle text-bold constraint-override-action"
                  onClick={() => setActionStatus('NONE')}
                >
                  Try again
                </button>
              </>
            )}
          </div>
        )
      }
    />
  );
});
