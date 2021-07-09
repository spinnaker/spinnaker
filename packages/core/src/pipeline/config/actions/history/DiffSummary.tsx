import React from 'react';

import { IDiffSummary } from '../../../../utils';

export interface IDiffSummaryProps {
  summary: IDiffSummary;
}

export function DiffSummary(props: IDiffSummaryProps) {
  const { summary } = props;

  return (
    <div className="diff-summary">
      {summary.additions > 0 && <span className="footer-additions">+ {summary.additions}</span>}
      {summary.removals > 0 && <span className="footer-removals">- {summary.removals}</span>}
    </div>
  );
}
