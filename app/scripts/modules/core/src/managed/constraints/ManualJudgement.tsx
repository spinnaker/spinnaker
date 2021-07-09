import React from 'react';

import { IConstraint } from '../../domain';

export const ManualJudgementTitle = ({ constraint }: { constraint: IConstraint }) => {
  switch (constraint.status) {
    case 'NOT_EVALUATED':
    case 'BLOCKED':
      return <>Manual judgement is blocked by other constraints</>;
    case 'PENDING':
      return <>Awaiting manual judgement</>;
    case 'OVERRIDE_PASS':
    case 'FORCE_PASS':
    case 'PASS':
      return <>Approved by {constraint.judgedBy}</>;
    case 'FAIL':
    case 'OVERRIDE_FAIL':
      return <>Rejected by {constraint.judgedBy}</>;

    default:
      console.error(new Error(`Unrecognized constraint status - ${JSON.stringify(constraint)}`));
      return <>Manual Judgement constraint - {constraint.status}</>;
  }
};
