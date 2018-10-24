import { isEmpty, isInteger, isString } from 'lodash';

export interface IDuration {
  hours: number;
  minutes: number;
}

export const defaultDurationObject = {
  hours: 0,
  minutes: 0,
};

export const defaultDurationString = 'PT0H0M';

export function parseDurationString(duration: string): IDuration {
  const defaultDurationObjectCopy = { ...defaultDurationObject };
  if (!isString(duration)) {
    return defaultDurationObjectCopy;
  }
  const durationComponents = duration.match(/PT(\d+)H(?:(\d+)M)?/i);
  if (isEmpty(durationComponents)) {
    return defaultDurationObjectCopy;
  }
  let hours = parseInt(durationComponents[1], 10);
  if (!isInteger(hours) || hours < 0) {
    hours = 0;
  }
  let minutes = parseInt(durationComponents[2], 10);
  if (!isInteger(minutes) || minutes < 0) {
    minutes = 0;
  }
  return { hours, minutes };
}
// Returns string parsable by Java.time.Duration.parse
// https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence
export function getDurationString(duration: IDuration): string {
  if (!duration) {
    return defaultDurationString;
  }
  let { hours, minutes } = duration;
  if (!isInteger(hours) || hours < 0) {
    hours = 0;
  }
  if (!isInteger(minutes) || minutes < 0) {
    minutes = 0;
  }
  return `PT${hours}H${minutes}M`;
}
