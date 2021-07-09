import { get } from 'lodash';
import { Duration } from 'luxon';
import React from 'react';

import { IStage } from '../../../../domain';
import { HelpField } from '../../../../help';
import { CheckboxInput, NumberInput } from '../../../../presentation';

const { useEffect, useState } = React;

export interface IOverrideTimeoutConfigProps {
  stageConfig: IStageConfig;
  stageTimeoutMs: number;
  updateStageField: (changes: Partial<IStage>) => void;
}

interface IStageConfig {
  supportsCustomTimeout: boolean;
}

const toHoursAndMinutes = (ms: number) => {
  if (!ms) {
    return { hours: 0, minutes: 0 };
  } else {
    const { hours, minutes } = Duration.fromMillis(ms).shiftTo('hours', 'minutes').toObject();

    return {
      hours: Math.floor(hours),
      minutes: Math.floor(minutes),
    };
  }
};

export const OverrideTimeout = (props: IOverrideTimeoutConfigProps) => {
  const [hours, setHours] = useState(0);
  const [minutes, setMinutes] = useState(0);
  const [overrideTimeout, setOverrideTimeout] = useState(false);

  useEffect(() => {
    stageChanged();
  }, [props.stageTimeoutMs]);

  const stageChanged = () => {
    if (props.stageTimeoutMs !== undefined) {
      enableTimeout();
    } else {
      clearTimeout();
    }
  };

  const synchronizeTimeout = (h: number, m: number) => {
    let timeout = 0;
    timeout += 60 * 60 * 1000 * h;
    timeout += 60 * 1000 * m;
    props.updateStageField({ stageTimeoutMs: timeout });
  };

  const toggleTimeout = () => {
    overrideTimeout ? clearTimeout() : enableTimeout();
  };

  const clearTimeout = () => {
    props.updateStageField({ stageTimeoutMs: undefined });
    setOverrideTimeout(false);
  };

  const enableTimeout = () => {
    setOverrideTimeout(true);
    props.updateStageField({
      stageTimeoutMs: props.stageTimeoutMs || null,
    });
    setHours(toHoursAndMinutes(props.stageTimeoutMs).hours);
    setMinutes(toHoursAndMinutes(props.stageTimeoutMs).minutes);
  };

  const isConfigurable = !!get(props.stageConfig, 'supportsCustomTimeout');

  if (isConfigurable) {
    return (
      <>
        <div className="form-group">
          <div className="col-md-9 col-md-offset-1">
            <div className="checkbox">
              <CheckboxInput
                text={
                  <>
                    <strong>Fail stage after a specific amount of time</strong>{' '}
                    <HelpField id="pipeline.config.timeout" />
                  </>
                }
                value={overrideTimeout}
                onChange={toggleTimeout}
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
                  inputClassName="form-control input-sm inline-number with-space-before"
                  min={0}
                  onChange={(e: React.ChangeEvent<any>) => {
                    setHours(e.target.value);
                    synchronizeTimeout(e.target.value, minutes);
                  }}
                  value={hours}
                />{' '}
                hours
                <NumberInput
                  inputClassName="form-control input-sm inline-number with-space-before"
                  min={0}
                  onChange={(e: React.ChangeEvent<any>) => {
                    setMinutes(e.target.value);
                    synchronizeTimeout(hours, e.target.value);
                  }}
                  value={minutes}
                />{' '}
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
