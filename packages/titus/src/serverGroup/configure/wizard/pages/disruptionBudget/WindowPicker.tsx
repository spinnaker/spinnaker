import { FormikProps } from 'formik';
import { get } from 'lodash';
import React from 'react';

import { ChecklistInput, FormikFormField, HelpField, NumberInput, ReactSelectInput, SpinFormik } from '@spinnaker/core';
import { IJobTimeWindow } from '../../../../../domain';

import { ITitusServerGroupCommand } from '../../../serverGroupConfiguration.service';

export interface IWindowPickerProps {
  formik: FormikProps<ITitusServerGroupCommand>;
  isDisabled: boolean;
}

export interface IWindowPickerState {
  addingNewWindow?: boolean;
}

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

interface IJobTimeWindowForm {
  days: string[];
  startHour: number;
  endHour: number;
  timeZone: string;
}

export class WindowPicker extends React.Component<IWindowPickerProps, IWindowPickerState> {
  constructor(props: IWindowPickerProps) {
    super(props);
    this.state = {
      addingNewWindow: false,
    };
  }

  private removeWindow(index: number, subIndex: number) {
    const { values, setFieldValue } = this.props.formik;
    const { timeWindows } = values.disruptionBudget;
    const toRemove = timeWindows[index];
    if (toRemove.hourlyTimeWindows.length === 1) {
      setFieldValue(
        'disruptionBudget.timeWindows',
        timeWindows.filter((w) => w !== toRemove),
      );
    } else {
      const subWindowToRemove = toRemove.hourlyTimeWindows[subIndex];
      const newWindows = toRemove.hourlyTimeWindows.filter((w) => w !== subWindowToRemove);
      setFieldValue(`disruptionBudget.timeWindows[${index}].hourlyTimeWindows`, newWindows);
    }
  }

  /**
   * The Budget API allows multiple hourly windows for a set of days; however, the UI is not really supportive of this,
   * so we'll add them as a single window for a single set of days. It's fine!
   */
  private configureNewWindow = () => {
    this.setState({ addingNewWindow: true });
  };

  private addNewWindow = (form: IJobTimeWindowForm) => {
    const { values, setFieldValue } = this.props.formik;
    const { days, startHour, endHour, timeZone } = form;
    const newWindow: IJobTimeWindow = {
      days,
      hourlyTimeWindows: [{ startHour, endHour }],
      timeZone,
    };
    setFieldValue('disruptionBudget.timeWindows', values.disruptionBudget.timeWindows.concat(newWindow));
    this.setState({ addingNewWindow: false });
  };

  private CurrentWindows = () => {
    const { disruptionBudget } = this.props.formik.values;
    const { isDisabled } = this.props;
    if (!disruptionBudget) {
      return null;
    }
    const { timeWindows } = disruptionBudget;
    return (
      <div className="sp-formItem">
        <div className="sp-formItem__left" />
        <div className="sp-formItem__right">
          {timeWindows.map((timeWindow, i) => {
            return timeWindow.hourlyTimeWindows.map((w, i2) => (
              <div
                className="sp-margin-l-yaxis sp-margin-m-right"
                style={{ display: 'inline-block' }}
                key={`${i}.${i2}`}
              >
                <div
                  key={`${i}.${i2}`}
                  className="horizontal center top sp-padding-m-yaxis sp-padding-s-xaxis"
                  style={{ backgroundColor: 'var(--color-dovegray)', borderRadius: '4px' }}
                >
                  <div className="sp-padding-m-right" style={{ color: 'var(--color-text-on-dark)', fontSize: '12px' }}>
                    <div>
                      <b>{timeWindow.days.map((d) => d.substr(0, 3)).join(', ')}</b>
                    </div>
                    <div>
                      <em>
                        {w.startHour}:00 - {w.endHour}:00 {timeWindow.timeZone}
                      </em>
                    </div>
                  </div>
                  {!isDisabled && (
                    <a
                      className="clickable"
                      style={{ color: 'var(--color-text-on-dark)' }}
                      onClick={() => this.removeWindow(i, i2)}
                    >
                      <span className="glyphicon glyphicon-remove" />
                    </a>
                  )}
                </div>
              </div>
            ));
          })}
        </div>
      </div>
    );
  };

  private NewWindowComponent = () => {
    if (!this.state.addingNewWindow) {
      return (
        <div className="sp-formItem">
          <div className="sp-formItem__left" />
          <div className="sp-formItem__right sp-margin-l-bottom">
            <a className="button primary" onClick={this.configureNewWindow}>
              <i className="fas fa-plus-circle" /> Define New Window
            </a>
          </div>
        </div>
      );
    }
    return (
      <SpinFormik<IJobTimeWindowForm>
        initialValues={{
          days: [],
          startHour: 10,
          endHour: 16,
          timeZone: get(this.props.formik.values.disruptionBudget, 'timeWindows[0].timeZone', 'PST'),
        }}
        onSubmit={this.addNewWindow}
        render={(formik) => (
          <>
            <FormikFormField
              name="days"
              label="Define a New Window"
              input={(props) => <ChecklistInput {...props} stringOptions={DAYS} />}
            />
            <FormikFormField
              name="startHour"
              label="Start"
              input={(props) => (
                <div>
                  <NumberInput {...props} min={0} max={formik.values.endHour} />
                  <HelpField expand={true} content="hour (0-24)" />
                </div>
              )}
            />
            <FormikFormField
              name="endHour"
              label="End"
              input={(props) => (
                <div>
                  <NumberInput {...props} min={formik.values.startHour} max={23} />
                  <HelpField expand={true} content="hour (0-24)" />
                </div>
              )}
            />
            <FormikFormField
              name="timeZone"
              label="Timezone"
              input={(props) => <ReactSelectInput {...props} clearable={false} stringOptions={['PST', 'UTC']} />}
            />
            <div className="sp-formItem">
              <div className="sp-formItem__left" />
              <div className="sp-formItem__right sp-margin-l-bottom">
                <button className="primary" type="submit" onClick={() => this.addNewWindow(formik.values)}>
                  {' '}
                  Add Window
                </button>
              </div>
            </div>
          </>
        )}
      />
    );
  };

  public render() {
    const { CurrentWindows, NewWindowComponent } = this;
    const { isDisabled } = this.props;
    return (
      <>
        <CurrentWindows />
        {!isDisabled && <NewWindowComponent />}
      </>
    );
  }
}
