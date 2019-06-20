import * as React from 'react';

import { IStage } from 'core/domain';
import { CheckboxInput, NumberInput } from 'core/presentation';
import { HelpContentsRegistry, HelpField } from 'core/help';

const { useEffect, useState } = React;

export interface IOverrideTimeoutConfigProps {
  stageConfig: IStageConfig;
  stageTimeoutMs: number;
  updateStageField: (changes: Partial<IStage>) => void;
}

interface IStageConfig {
  defaultTimeoutMs: number;
}

export const OverrideTimeout = (props: IOverrideTimeoutConfigProps) => {
  const [hours, setHours] = useState(0);
  const [minutes, setMinutes] = useState(0);
  const [overrideTimeout, setOverrideTimeout] = useState(false);
  const [configurable, setConfigurable] = useState(false);
  const [defaults, setDefaults] = useState({ hours: 0, minutes: 0 });
  const helpContent = HelpContentsRegistry.getHelpField('pipeline.config.timeout');

  useEffect(() => {
    setOverrideValues(overrideTimeout);
  }, [props.stageConfig]);

  const setOverrideValues = (newOverrideTimeout: boolean) => {
    const stageDefaults = props.stageConfig ? props.stageConfig.defaultTimeoutMs : null;
    const originalOverrideTimeout = newOverrideTimeout === true;
    const shouldRemoveOverride = originalOverrideTimeout === false;

    setConfigurable(!!stageDefaults);
    setDefaults(toHoursAndMinutes(stageDefaults));

    if (shouldRemoveOverride) {
      props.updateStageField({ stageTimeoutMs: undefined });
    } else if (originalOverrideTimeout || props.stageTimeoutMs !== undefined) {
      // Either vm.overrideTimeout was originally true, or forcing to true because stageTimeoutMs is defined
      setOverrideTimeout(true);
      props.updateStageField({
        stageTimeoutMs: props.stageTimeoutMs || stageDefaults,
      });
      setHours(toHoursAndMinutes(props.stageTimeoutMs).hours);
      setMinutes(toHoursAndMinutes(props.stageTimeoutMs).minutes);
    }
  };

  const synchronizeTimeout = (h: number, m: number) => {
    let timeout = 0;
    timeout += 60 * 60 * 1000 * h;
    timeout += 60 * 1000 * m;
    props.updateStageField({ stageTimeoutMs: timeout });
  };

  function toHoursAndMinutes(ms: number) {
    if (!ms) {
      return { hours: 0, minutes: 0 };
    } else {
      const seconds = ms / 1000;
      return {
        hours: Math.floor(seconds / 3600),
        minutes: Math.floor(seconds / 60) % 60,
      };
    }
  }

  if (configurable) {
    return (
      <>
        <div className="form-group">
          <div className="col-md-9 col-md-offset-1">
            <div className="checkbox">
              <CheckboxInput
                text={
                  <>
                    <strong> Override default timeout</strong> (
                    {defaults.hours > 0 && (
                      <span>
                        {defaults.hours} {defaults.hours > 1 ? 'hours' : 'hour'}{' '}
                      </span>
                    )}
                    {defaults.minutes > 0 && (
                      <span>
                        {defaults.minutes} {defaults.minutes > 1 ? 'minutes' : 'minute'}
                      </span>
                    )}
                    )<HelpField content={helpContent} />
                  </>
                }
                value={overrideTimeout}
                onChange={() => {
                  const newOverrideTimeout = !overrideTimeout;
                  setOverrideTimeout(newOverrideTimeout);
                  setOverrideValues(newOverrideTimeout);
                }}
              />
            </div>
          </div>
        </div>
        {overrideTimeout && (
          <div>
            <div className="form-group form-inline">
              <div className="col-md-9 col-md-offset-1 checkbox-padding">
                Fail this stage if it takes longer than
                <NumberInput
                  inputClassName={'form-control input-sm inline-number with-space-before'}
                  min={0}
                  onChange={(e: React.ChangeEvent<any>) => {
                    setHours(e.target.value);
                    synchronizeTimeout(e.target.value, minutes);
                  }}
                  value={hours}
                />
                hours
                <NumberInput
                  inputClassName={'form-control input-sm inline-number with-space-before'}
                  min={0}
                  onChange={(e: React.ChangeEvent<any>) => {
                    setMinutes(e.target.value);
                    synchronizeTimeout(hours, e.target.value);
                  }}
                  value={minutes}
                />
                minutes to complete
              </div>
            </div>
          </div>
        )}
      </>
    );
  } else {
    return <></>;
  }
};
