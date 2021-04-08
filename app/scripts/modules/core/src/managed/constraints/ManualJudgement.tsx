import React from 'react';

import { IConstraint } from 'core/domain';

const NO_FAILURE_MESSAGE = 'no details available';

export const ManualJudgementTitle = ({ constraint }: { constraint: IConstraint }) => {
  switch (constraint.status) {
    case 'NOT_EVALUATED':
      return <>Manual judgement will be required before promotion</>;
    case 'PENDING':
      return <>Awaiting manual judgement</>;
    case 'OVERRIDE_PASS':
    case 'OVERRIDE_FAIL':
      return (
        <span>
          <span>Manually {constraint.status === 'OVERRIDE_PASS' ? 'approved' : 'rejected'}</span>{' '}
          <span className="text-regular">—</span> <span className="text-regular">by {constraint.judgedBy}</span>
        </span>
      );
    case 'FAIL':
      return (
        <span>
          {constraint.comment?.includes('Timed out') ? 'Manual judgement was rejected' : 'Manual judgement failed'}{' '}
          <span className="text-regular">—</span>{' '}
          <span className="text-regular">{constraint.comment || NO_FAILURE_MESSAGE}</span>
        </span>
      );
    default:
      console.error(new Error(`Unrecognized constraint status - ${JSON.stringify(constraint)}`));
      return (
        <>
          {constraint.type} constraint - {constraint.status}
        </>
      );
  }
};
