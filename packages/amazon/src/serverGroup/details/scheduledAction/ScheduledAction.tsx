import React from 'react';

import { IScheduledAction } from '../../../domain';

export interface IScheduledActionProps {
  action: IScheduledAction;
}

export class ScheduledAction extends React.Component<IScheduledActionProps> {
  public render() {
    const { action } = this.props;
    return (
      <dl className="horizontal-when-filters-collapsed" style={{ marginBottom: '20px' }}>
        <dt>Schedule</dt>
        <dd>{action.recurrence}</dd>
        {action.minSize !== undefined && <dt>Min Size</dt>}
        {action.minSize !== undefined && <dd>{action.minSize}</dd>}
        {action.maxSize !== undefined && <dt>Max Size</dt>}
        {action.maxSize !== undefined && <dd>{action.maxSize}</dd>}
        {action.desiredCapacity !== undefined && <dt>Desired Size</dt>}
        {action.desiredCapacity !== undefined && <dd>{action.desiredCapacity}</dd>}
      </dl>
    );
  }
}
