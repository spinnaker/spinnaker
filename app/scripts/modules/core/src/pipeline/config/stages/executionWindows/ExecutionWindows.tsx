import { format } from 'date-fns';
import { extend, get, isEqual } from 'lodash';
import React from 'react';
import { Option } from 'react-select';

import { DEFAULT_SKIP_WINDOW_TEXT } from './ExecutionWindowActions';
import { ExecutionWindowDayPicker } from './ExecutionWindowDayPicker';
import { IStage } from '../../../../domain';
import { IJitter, IRestrictedExecutionWindow, ITimelineWindow, IWindow } from './executionWindowsConfig';
import {
  CheckboxInput,
  NumberInput,
  ReactSelectInput,
  TextAreaInput,
  TextInput,
  Tooltip,
} from '../../../../presentation';
import { SystemTimezone } from '../../../../utils/SystemTimezone';
import { TimePickerOptions } from '../../../../utils/TimePickerOptions';

import './executionWindows.less';

const { useState } = React;

export interface IExecutionWindowsConfigProps {
  restrictExecutionDuringTimeWindow: boolean | string;
  restrictedExecutionWindow: IRestrictedExecutionWindow;
  skipWindowText: string;
  updateStageField: (changes: Partial<IStage>) => void;
}

export const ExecutionWindows = (props: IExecutionWindowsConfigProps) => {
  const SELECT_EXECUTION_WINDOW_CLASSES =
    'visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select';
  const defaultJitter: IJitter = {
    minDelay: 0,
    maxDelay: 600,
    enabled: false,
    skipManual: false,
  };
  const jitter = get(props.restrictedExecutionWindow, 'jitter', defaultJitter);
  const allowlist = get(props.restrictedExecutionWindow, 'whitelist', []);
  const days = get(props.restrictedExecutionWindow, 'days', []);
  const [timelineWindows, setTimelineWindows] = useState(getTimelineWindows(allowlist));
  const [enableCustomSkipWindowText, setEnableCustomSkipWindowText] = useState(!!props.skipWindowText);
  const defaultSkipWindowText = DEFAULT_SKIP_WINDOW_TEXT;
  const isWindowExpression =
    typeof props.restrictExecutionDuringTimeWindow === 'string' &&
    props.restrictExecutionDuringTimeWindow.includes('${');
  const hours = TimePickerOptions.getHours();
  const minutes = TimePickerOptions.getMinutes();
  const dividers = hours.map((hour) => ({
    label: hour.label,
    left: (hour.value / 24) * 100 + '%',
  }));

  // if the user edits the stage as JSON, we'll get a re-render, but the state will be possibly stale, so reset
  if (!isEqual(getTimelineWindows(allowlist), timelineWindows)) {
    setTimelineWindows(getTimelineWindows(allowlist));
  }

  const jitterUpdated = (changes: Partial<IJitter>) => {
    let newJitterValue: IJitter = { ...jitter };
    extend(newJitterValue, changes);
    if (newJitterValue.enabled) {
      if (newJitterValue.minDelay >= 0 && newJitterValue.maxDelay <= newJitterValue.minDelay) {
        newJitterValue.maxDelay = newJitterValue.minDelay + 1;
      }
    } else {
      newJitterValue = defaultJitter;
    }
    const restrictedExecutionWindow = {
      ...props.restrictedExecutionWindow,
      jitter: newJitterValue,
    };
    props.updateStageField({ restrictedExecutionWindow });
  };

  const addExecutionWindow = (): void => {
    const newExecutionWindow = {
      startHour: 0,
      startMin: 0,
      endHour: 0,
      endMin: 0,
    };
    const restrictedExecutionWindow = {
      ...(props.restrictedExecutionWindow || {}),
      whitelist: [...allowlist, newExecutionWindow],
    };
    props.updateStageField({ restrictedExecutionWindow });
  };

  const removeWindow = (index: number): void => {
    const newAllowlist = [...allowlist];
    newAllowlist.splice(index, 1);
    const restrictedExecutionWindow = {
      ...props.restrictedExecutionWindow,
      whitelist: newAllowlist,
    };
    props.updateStageField({ restrictedExecutionWindow });
  };

  const windowDaysUpdated = (newDays: number[]): void => {
    const restrictedExecutionWindow = {
      ...props.restrictedExecutionWindow,
      days: newDays,
    };
    props.updateStageField({ restrictedExecutionWindow });
  };

  const updateAllowlist = (changes: Partial<IWindow>, index: number) => {
    const newAllowlist = [...allowlist];
    extend(newAllowlist[index], changes);
    const restrictedExecutionWindow = {
      ...props.restrictedExecutionWindow,
      whitelist: newAllowlist,
    };
    props.updateStageField({ restrictedExecutionWindow });
    setTimelineWindows(getTimelineWindows(newAllowlist));
  };

  function getTimelineWindows(w: IWindow[]): ITimelineWindow[] {
    const windows: ITimelineWindow[] = [];
    w.forEach((window: IWindow) => {
      const start = window.startHour * 60 + window.startMin;
      const end = window.endHour * 60 + window.endMin;

      // split into two windows
      if (start > end) {
        const firstWindow = {
          startMin: window.startMin,
          startHour: window.startHour,
          endMin: 0,
          endHour: 24,
          wrapEnd: true,
        };
        const secondWindow = {
          startMin: 0,
          startHour: 0,
          endMin: window.endMin,
          endHour: window.endHour,
        };
        windows.push(buildTimelineWindow(firstWindow, window));
        windows.push(buildTimelineWindow(secondWindow, window));
      } else {
        windows.push(buildTimelineWindow(window));
      }
    });
    return windows;
  }

  function buildTimelineWindow(window: IWindow, originalWindow?: IWindow): ITimelineWindow {
    const labelRef = originalWindow || window;
    const timelineWindow = {
      style: getWindowStyle(window),
      start: new Date(2000, 1, 1, labelRef.startHour, labelRef.startMin),
      end: new Date(2000, 1, 1, labelRef.endHour, labelRef.endMin),
      displayStart: new Date(2000, 1, 1, window.startHour, window.startMin),
      displayEnd: new Date(2000, 1, 1, window.endHour, window.endMin),
    };
    if (window.wrapEnd) {
      timelineWindow.displayEnd = new Date(2000, 1, 1, 23, 59, 59, 999);
    }
    return timelineWindow;
  }

  function getWindowStyle(window: IWindow): any {
    const dayMinutes = 24 * 60;
    const start = window.startHour * 60 + window.startMin;
    const end = window.endHour * 60 + window.endMin;
    const width = ((end - start) / dayMinutes) * 100;
    const startOffset = (start / dayMinutes) * 100;

    return {
      width: width + '%',
      left: startOffset + '%',
    };
  }

  return (
    <div className="execution-windows">
      <div className="form-group">
        <div className="col-md-9 col-md-offset-1">
          {isWindowExpression && (
            <div className="row" style={{ marginTop: '20px' }}>
              <strong> Restrict execution to specific time windows when this expression is true</strong>
              <input type="text" className="form-control input-sm" />
              <TextInput
                value={props.restrictExecutionDuringTimeWindow}
                onChange={(e: React.ChangeEvent<any>) =>
                  props.updateStageField({ restrictExecutionDuringTimeWindow: e.target.value })
                }
              />
            </div>
          )}
          {!isWindowExpression && (
            <div className="checkbox">
              <CheckboxInput
                text={<strong> Restrict execution to specific time windows</strong>}
                value={props.restrictExecutionDuringTimeWindow}
                onChange={() =>
                  props.updateStageField({
                    restrictExecutionDuringTimeWindow: !props.restrictExecutionDuringTimeWindow,
                  })
                }
              />
            </div>
          )}
        </div>
      </div>
      {props.restrictExecutionDuringTimeWindow && (
        <div>
          <div className="row">
            <div className="col-md-10 col-md-offset-1">
              <h5>
                <strong>Days of the Week</strong>
                <small>(No days selected implies execution on any day if triggered)</small>
              </h5>
              <div className="execution-window-days-of-week">
                <ExecutionWindowDayPicker days={days} onChange={windowDaysUpdated} />
              </div>
            </div>
          </div>
          <div className="row">
            <div className="col-md-10 col-md-offset-1">
              <p>
                <strong>Time of Day</strong>
              </p>
            </div>
          </div>
          <div className="row">
            <div className="col-md-10 col-md-offset-1">
              <div className="execution-window-graph">
                <div className="execution-day">
                  {timelineWindows.map((w, i) => {
                    return (
                      <Tooltip key={i} value={format(w.start, 'HH:mm') + ' - ' + format(w.end, 'HH:mm')}>
                        <div className="execution-window" style={w.style} />
                      </Tooltip>
                    );
                  })}
                </div>
                <div className="divider-label-fill" />
                <div className="dividers">
                  {dividers.map((d, i) => {
                    return (
                      <div className="divider" key={i} style={{ left: d.left }}>
                        <div className="divider-filler" />
                        <div className="divider-label">{d.label}</div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
          <div className="form-group">
            <p className="col-md-9 col-md-offset-1 form-control-static">
              This stage will only run within the following windows (all times in <SystemTimezone />
              ):
            </p>
          </div>
          {allowlist.map((w: IWindow, i: number) => {
            return (
              <div className="window-entry" key={i}>
                <div className="form-group">
                  <div className="col-md-6 col-md-offset-1">
                    <span className="start-end-divider">From</span>
                    <ReactSelectInput
                      clearable={false}
                      inputClassName={SELECT_EXECUTION_WINDOW_CLASSES}
                      value={w.startHour}
                      options={hours.map((h) => ({ label: h.label, value: h.value }))}
                      onChange={(option: Option<number>) => {
                        updateAllowlist({ startHour: option.target.value }, i);
                      }}
                    />
                    <span> : </span>
                    <ReactSelectInput
                      clearable={false}
                      inputClassName={SELECT_EXECUTION_WINDOW_CLASSES}
                      value={w.startMin}
                      options={minutes.map((m) => ({ label: m.label, value: m.value }))}
                      onChange={(option: Option<number>) => {
                        updateAllowlist({ startMin: option.target.value }, i);
                      }}
                    />
                    <span className="start-end-divider"> to </span>
                    <ReactSelectInput
                      clearable={false}
                      inputClassName={SELECT_EXECUTION_WINDOW_CLASSES}
                      value={w.endHour}
                      options={hours.map((h) => ({ label: h.label, value: h.value }))}
                      onChange={(option: Option<number>) => {
                        updateAllowlist({ endHour: option.target.value }, i);
                      }}
                    />
                    <span> : </span>
                    <ReactSelectInput
                      clearable={false}
                      inputClassName={SELECT_EXECUTION_WINDOW_CLASSES}
                      value={w.endMin}
                      options={minutes.map((m) => ({ label: m.label, value: m.value }))}
                      onChange={(option: Option<number>) => {
                        updateAllowlist({ endMin: option.target.value }, i);
                      }}
                    />
                    <Tooltip value={'Remove window'}>
                      <button className="btn-link btn-remove" onClick={() => removeWindow(i)}>
                        <span className="glyphicon glyphicon-trash" />
                        <span className="sr-only">Remove window</span>
                      </button>
                    </Tooltip>
                  </div>
                </div>
              </div>
            );
          })}
          <div className="row">
            <div className="col-md-6 col-md-offset-1">
              <button className="btn btn-block btn-add-trigger add-new" onClick={() => addExecutionWindow()}>
                <span className="glyphicon glyphicon-plus-sign" /> Add an execution window
              </button>
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-9 col-md-offset-1">
              <div className="checkbox">
                <CheckboxInput
                  text={<strong> Add random jitter to start of execution window</strong>}
                  value={jitter.enabled}
                  onChange={() => {
                    jitterUpdated({ enabled: !jitter.enabled });
                  }}
                />
              </div>
            </div>
          </div>
          {jitter.enabled && (
            <div>
              <div className="form-group">
                <div className="col-md-10 col-md-offset-1 checkbox-padding">
                  Randomly wait between{' '}
                  <NumberInput
                    inputClassName={'form-control input-sm inline-number with-space-before'}
                    min={0}
                    value={jitter.minDelay}
                    onChange={(e: React.ChangeEvent<any>) => {
                      jitterUpdated({ minDelay: e.target.value });
                    }}
                  />{' '}
                  and{' '}
                  <NumberInput
                    inputClassName={'form-control input-sm inline-number with-space-before'}
                    max={60 * 60 * 72 - 1}
                    value={jitter.maxDelay}
                    onChange={(e: React.ChangeEvent<any>) => {
                      jitterUpdated({ maxDelay: e.target.value });
                    }}
                  />{' '}
                  seconds when entering execution window.
                </div>
              </div>
              <div className="form-group">
                <div className="col-md-10 col-md-offset-1 checkbox-padding">
                  <CheckboxInput
                    text={<strong> Skip jitter when manually triggering pipeline</strong>}
                    value={jitter.skipManual}
                    onChange={() => {
                      jitterUpdated({ skipManual: !jitter.skipManual });
                    }}
                  />
                </div>
              </div>
            </div>
          )}
          <div className="form-group">
            <div className="col-md-10 col-md-offset-1">
              <CheckboxInput
                text={<strong> Show custom warning when users skip execution window</strong>}
                value={enableCustomSkipWindowText}
                onChange={() => setEnableCustomSkipWindowText(!enableCustomSkipWindowText)}
              />
            </div>
            {enableCustomSkipWindowText && (
              <div className="col-md-10 col-md-offset-1 checkbox-padding">
                <TextAreaInput
                  rows={4}
                  onChange={(e: React.ChangeEvent<any>) => props.updateStageField({ skipWindowText: e.target.value })}
                  placeholder={"Default text: '" + defaultSkipWindowText + "' (HTML is okay)"}
                  style={{ marginTop: '5px' }}
                  value={props.skipWindowText}
                />
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
