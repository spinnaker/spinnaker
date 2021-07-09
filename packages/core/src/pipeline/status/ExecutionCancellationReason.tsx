import React from 'react';

import './executionCancellationReason.less';

interface IExecutionCancellationReasonProps {
  cancellationReason: string;
}

export function ExecutionCancellationReason({ cancellationReason }: IExecutionCancellationReasonProps) {
  const [isExpanded, setIsExpanded] = React.useState(false);
  return (
    <>
      <div className="execution-cancellation-reason-button">
        <span className="clickable btn-link" onClick={() => setIsExpanded(!isExpanded)}>
          <span className={`small glyphicon ${isExpanded ? 'glyphicon-chevron-down' : 'glyphicon-chevron-right'}`} />
          Cancellation Reason
        </span>
      </div>
      {isExpanded && <pre className="execution-cancellation-reason-text">{cancellationReason}</pre>}
    </>
  );
}
