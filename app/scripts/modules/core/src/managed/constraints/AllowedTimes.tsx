import { padStart, sortBy } from 'lodash';
import React from 'react';

import { AllowedTimeWindow, IAllowedTimesConstraint } from 'core/domain';

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
  const prettyTimezone = timeZone === 'America/Los_Angeles' ? 'PST' : timeZone;

  return `${hoursString.join(', ')} on ${daysString.join(', ')} (${prettyTimezone})`;
};

const DeploymentWindow = ({ allowedTimes, timezone }: IAllowedTimesConstraint['attributes']) => {
  return (
    <ul className="sp-margin-xs-top sp-padding-l-left">
      {allowedTimes.map((window, index) => (
        <li key={index}>{timeWindowToString(window, timezone)}</li>
      ))}
    </ul>
  );
};

const getTitle = (constraint: IAllowedTimesConstraint) => {
  switch (constraint.status) {
    case 'FAIL':
      return 'Failed to deploy within the allowed windows';
    case 'OVERRIDE_PASS':
      return 'Deployment window constraint was overridden';
    case 'PASS':
      return 'Deployed during one of the previous open windows';
    case 'PENDING':
      return `Deployment can only occur during the following window${
        constraint.attributes.allowedTimes.length > 1 ? 's' : ''
      }:`;
    default:
      return `Allowed times constraint - ${constraint.status}:`;
  }
};

export const AllowedTimesTitle = ({ constraint }: { constraint: IAllowedTimesConstraint }) => {
  return <>{getTitle(constraint)}</>;
};

export const AllowedTimesDescription = ({ constraint }: { constraint: IAllowedTimesConstraint }) => {
  return <DeploymentWindow {...constraint.attributes} />;
};
