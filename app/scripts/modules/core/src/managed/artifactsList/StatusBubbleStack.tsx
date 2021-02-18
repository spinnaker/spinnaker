import { take } from 'lodash';
import React from 'react';

import { IStatusBubbleProps, StatusBubble } from '../StatusBubble';

import './StatusBubbleStack.less';

export interface IStatusBubbleStackProps {
  borderColor: string;
  maxBubbles: number;
  statuses: Array<Pick<IStatusBubbleProps, 'iconName' | 'appearance'>>;
}

export const StatusBubbleStack = ({ borderColor, maxBubbles, statuses }: IStatusBubbleStackProps) => {
  // We only have limited space in the UI and we cannot show all the statuses in the stack, so only take the first few
  // important statuses.
  const statusBubblesToRender = take(statuses, statuses.length <= maxBubbles ? maxBubbles : maxBubbles - 1);
  const hiddenStatusBubbleCount = statuses.length - statusBubblesToRender.length;

  return (
    <div className="StatusBubbleStack flex-container-h middle">
      {statusBubblesToRender.map((status, index) => (
        <div
          className="StatusBubbleContainer"
          key={status.iconName}
          style={{
            backgroundColor: borderColor,
            zIndex: statusBubblesToRender.length - index,
          }}
        >
          <StatusBubble iconName={status.iconName} appearance={status.appearance} size="small" />
        </div>
      ))}
      {!!hiddenStatusBubbleCount && <div className="HiddenStatusCount">+{hiddenStatusBubbleCount}</div>}
    </div>
  );
};
