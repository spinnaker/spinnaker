import React from 'react';

import { Tooltip } from 'core/presentation';

import { DurationRender } from '../../RelativeTimestamp';
import { VersionOperationIcon } from './VersionOperation';
import { QueryVerification, QueryVerificationStatus } from '../types';
import { TOOLTIP_DELAY } from '../../utils/defaults';

import './Verifications.less';

const statusToText: {
  [key in QueryVerificationStatus]: string;
} = {
  FAIL: 'failed',
  FORCE_PASS: 'has been overridden',
  PASS: 'passed',
  PENDING: 'in progress',
  NOT_EVALUATED: 'has not started yet',
};

interface IVerificationProps {
  verification: QueryVerification;
}

const Verification = ({ verification }: IVerificationProps) => {
  const status = verification.status || 'PENDING';
  const { link, startedAt, completedAt } = verification;
  return (
    <div className="version-verification">
      <VersionOperationIcon status={status} />
      <div className="verification-content">
        Verification {verification.id} {statusToText[status]}{' '}
        {startedAt && (
          <span className="verification-metadata verification-runtime">
            <Tooltip value="Runtime duration" delayShow={TOOLTIP_DELAY}>
              <i className="far fa-clock" />
            </Tooltip>
            <DurationRender {...{ startedAt, completedAt }} />
          </span>
        )}
        {link && (
          <span className="verification-metadata">
            <a href={link} target="_blank" rel="noreferrer">
              View logs
            </a>
          </span>
        )}
      </div>
    </div>
  );
};

interface IVerificationsProps {
  verifications: QueryVerification[];
}

export const Verifications = ({ verifications }: IVerificationsProps) => {
  return (
    <div className="Verifications">
      {verifications.map((verification) => (
        <Verification key={verification.id} verification={verification} />
      ))}
    </div>
  );
};
