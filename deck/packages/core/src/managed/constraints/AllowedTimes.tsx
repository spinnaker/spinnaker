import { padStart, sortBy } from 'lodash';
import React from 'react';

import type { AllowedTimeWindow, IAllowedTimesConstraint } from '../../domain';

import './AllowedTimes.less';

export interface GroupRange {
  start: number;
  end: number;
}

export const groupConsecutiveNumbers = (values: number[]) => {
  const groups: GroupRange[] = [];
  for (const value of sortBy(values)) {
    const lastGroup = groups[groups.length - 1];
    if (!lastGroup || lastGroup.end !== value - 1) {
      groups.push({ start: value, end: value });
    } else {
      lastGroup.end = value;
    }
  }
  return groups;
};

const DAYS_TO_STRING: { [key: number]: string } = {
  1: 'Mon',
  2: 'Tue',
  3: 'Wed',
  4: 'Thu',
  5: 'Fri',
  6: 'Sat',
  7: 'Sun',
};

const hourToString = (hour: number) => {
  const hourString = padStart(`${hour === 25 ? 1 : hour}`, 2, '0');
  return `${hourString}:00`;
};

export const groupHours = (hours: number[]) => {
  const hourGroups = groupConsecutiveNumbers(hours);
  // We add an hour to the end of the range, as the backend treats the range as inclusive (e.g. can promote until the end of the last hour)
  return hourGroups.map((group) => `${hourToString(group.start)}-${hourToString(group.end + 1)}`);
};

export const groupDays = (days: number[]) => {
  const dayGroups = groupConsecutiveNumbers(days);
  const daysString = dayGroups.map((group) => {
    if (group.start === group.end) {
      return DAYS_TO_STRING[group.start];
    }
    return `${DAYS_TO_STRING[group.start]}-${DAYS_TO_STRING[group.end]}`;
  });
  return daysString;
};

const timeWindowToString = (window: AllowedTimeWindow, timeZone = 'PST') => {
  const hoursString = groupHours(window.hours);
  const daysString = groupDays(window.days);
  // A special treatment for PST as it's the most common timezone
  const prettyTimezone = timeZone === 'America/Los_Angeles' ? 'PST' : timeZone || 'PST';

  return `${hoursString.join(', ')} on ${daysString.join(', ')} (${prettyTimezone})`;
};

const DeploymentWindow = ({ attributes }: { attributes: IAllowedTimesConstraint['attributes'] }) => {
  if (!attributes) return null;
  const { allowedTimes, timezone, actualDeploys, maxDeploys } = attributes;
  return (
    <div className="DeploymentWindow">
      <ul className="sp-margin-xs-top sp-padding-l-left sp-margin-2xs-bottom">
        {allowedTimes.map((window, index) => (
          <li key={index}>{timeWindowToString(window, timezone)}</li>
        ))}
      </ul>
      {maxDeploys !== null && (
        <div className="sp-margin-xs-top">
          Deployed {actualDeploys || 0} out of {maxDeploys} times in the current window
        </div>
      )}
    </div>
  );
};

export const getAllowedTimesStatus = ({ constraint }: { constraint: IAllowedTimesConstraint }): string => {
  const maxDeploys = constraint.attributes?.maxDeploys;

  switch (constraint.status) {
    case 'BLOCKED':
      return 'waiting for other constraints';
    case 'OVERRIDE_PASS':
    case 'FORCE_PASS':
      return 'overridden by user';
    case 'PASS':
      return 'Deployed during the allowed windows';
    case 'FAIL':
    case 'PENDING':
      if (maxDeploys !== undefined && (constraint.attributes?.actualDeploys || 0) >= maxDeploys) {
        return `reached the maximum allowed times per window`;
      }
      return `can only occur during the allowed window${
        (constraint.attributes?.allowedTimes.length ?? 0) > 1 ? 's' : ''
      }`;
    default:
      return constraint.status;
  }
};

export const AllowedTimesDescription = ({ constraint }: { constraint: IAllowedTimesConstraint }) => {
  return <DeploymentWindow attributes={constraint.attributes} />;
};
