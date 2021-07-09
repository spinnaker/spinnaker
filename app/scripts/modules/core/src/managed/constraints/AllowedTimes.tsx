import { padStart, sortBy } from 'lodash';
import React from 'react';

import { AllowedTimeWindow, IAllowedTimesConstraint } from '../../domain';

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
  const { allowedTimes, timezone } = attributes;
  return (
    <ul className="sp-margin-xs-top sp-padding-l-left sp-margin-2xs-bottom">
      {allowedTimes.map((window, index) => (
        <li key={index}>{timeWindowToString(window, timezone)}</li>
      ))}
    </ul>
  );
};

const getTitle = (constraint: IAllowedTimesConstraint) => {
  switch (constraint.status) {
    case 'OVERRIDE_PASS':
    case 'FORCE_PASS':
      return 'Deployment window constraint was overridden';
    case 'PASS':
      return 'Deployed during one of the allowed windows';
    case 'FAIL':
    case 'PENDING':
      return `Deployment can only occur during the provided window${
        (constraint.attributes?.allowedTimes.length ?? 0) > 1 ? 's' : ''
      }`;
    default:
      return `Allowed times constraint - ${constraint.status}:`;
  }
};

export const AllowedTimesTitle = ({ constraint }: { constraint: IAllowedTimesConstraint }) => {
  return <>{getTitle(constraint)}</>;
};

export const AllowedTimesDescription = ({ constraint }: { constraint: IAllowedTimesConstraint }) => {
  return <DeploymentWindow attributes={constraint.attributes} />;
};
