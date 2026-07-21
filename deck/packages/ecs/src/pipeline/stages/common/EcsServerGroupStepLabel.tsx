import React from 'react';

export interface IEcsServerGroupStepLabelProps {
  action: string;
  step: any;
  useSource?: boolean;
}

export function EcsServerGroupStepLabel({ action, step, useSource }: IEcsServerGroupStepLabelProps) {
  const serverGroupName = useSource ? step.context?.source?.serverGroupName : step.context?.serverGroupName;
  return (
    <span className="task-label">
      {action}: {serverGroupName} ({step.context?.region})
    </span>
  );
}
