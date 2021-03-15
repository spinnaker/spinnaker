import React from 'react';
import { AllowedTimeWindow } from 'core/domain';

const DAYS_TO_STRING: { [key: number]: string } = {
  1: 'Mon',
  2: 'Tue',
  3: 'Wed',
  4: 'Thu',
  5: 'Fri',
  6: 'Sat',
  7: 'Sun',
};

const timeWindowToString = (window: AllowedTimeWindow) => {
  // TODO: group by hours on the backend.
  const daysString = window.days.map((day) => DAYS_TO_STRING[day]);
  return `${window.hours.join(', ')} PST on ${daysString.join(', ')}`;
};

export const DeploymentWindow: React.FC<{ windows: AllowedTimeWindow[] }> = ({ windows }) => {
  return (
    <div className="text-regular">
      {windows.map((window, index) => (
        <div key={index}>{timeWindowToString(window)}</div>
      ))}
    </div>
  );
};
