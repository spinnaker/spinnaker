import React from 'react';
import { DAYS_OF_WEEK } from './daysOfWeek';

export interface IExecutionWindowDayPickerConfigProps {
  days?: number[];
  onChange: (days: number[]) => void;
}

export const ExecutionWindowDayPicker = (props: IExecutionWindowDayPickerConfigProps) => {
  const daySelected = (ordinal: number): boolean => {
    if (props.days) {
      return props.days.includes(ordinal);
    } else {
      return false;
    }
  };

  const all = (): void => {
    props.onChange([1, 2, 3, 4, 5, 6, 7]);
  };

  const none = (): void => {
    props.onChange([]);
  };

  const weekdays = (): void => {
    props.onChange([2, 3, 4, 5, 6]);
  };

  const weekend = (): void => {
    props.onChange([1, 7]);
  };

  const updateModel = (day: any): void => {
    const days = props.days ? [...props.days] : [];
    if (days.includes(day.ordinal)) {
      days.splice(days.indexOf(day.ordinal), 1);
    } else {
      days.push(day.ordinal);
    }
    props.onChange(days);
  };

  return (
    <>
      <div className="btn-group">
        {DAYS_OF_WEEK.map((d, i) => (
          <button
            className={'btn btn-default ' + (daySelected(d.ordinal) ? 'active' : '')}
            key={i}
            onClick={() => updateModel(d)}
            type="button"
          >
            {d.label}
          </button>
        ))}
      </div>
      <div className="button-controls">
        <span className="btn btn-link" onClick={all}>
          All
        </span>
        <span className="btn btn-link" onClick={none}>
          None
        </span>
        <span className="btn btn-link" onClick={weekdays}>
          Weekdays
        </span>
        <span className="btn btn-link" onClick={weekend}>
          Weekend
        </span>
      </div>
    </>
  );
};
