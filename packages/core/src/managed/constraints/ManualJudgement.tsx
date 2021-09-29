import type { IConstraint } from '../../domain';

export const getManualJudgementStatus = ({ constraint }: { constraint: IConstraint }): string => {
  switch (constraint.status) {
    case 'NOT_EVALUATED':
    case 'BLOCKED':
      return 'waiting for other constraints';
    case 'PENDING':
      return 'pending';
    case 'OVERRIDE_PASS':
    case 'FORCE_PASS':
    case 'PASS':
      return `approved by ${constraint.judgedBy}`;
    case 'FAIL':
    case 'OVERRIDE_FAIL':
      return `rejected by ${constraint.judgedBy}`;

    default:
      console.error(new Error(`Unrecognized constraint status - ${JSON.stringify(constraint)}`));
      return constraint.status;
  }
};
