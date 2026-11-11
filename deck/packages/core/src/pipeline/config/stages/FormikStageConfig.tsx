import type { FormikErrors, FormikProps } from 'formik';
import React from 'react';

import type { Application } from '../../../application';
import type { IPipeline, IStage, ITrigger } from '../../../domain';
import { LayoutProvider, ResponsiveFieldLayout, SpinFormik, WatchValue } from '../../../presentation';

export interface IFormikStageConfigInjectedProps {
  application: Application;
  pipeline: IPipeline;
  formik: FormikProps<IStage>;
}

export type IContextualValidator = (
  values: IStage | ITrigger,
  context: any,
) => FormikErrors<IStage> | Promise<FormikErrors<IStage>>;

export interface IFormikStageConfigProps {
  application: Application;
  stage: IStage;
  pipeline: IPipeline;
  validate?: IContextualValidator;
  render: (props: IFormikStageConfigInjectedProps) => React.ReactNode;
  onChange?: (changes: Partial<IStage>) => void;
}

export type IStageValidator = (values: IStage) => void | object | Promise<FormikErrors<IStage>>;

const decorate = (validate: IContextualValidator, props: IFormikStageConfigProps): IStageValidator => {
  const { application, pipeline } = props;
  return (values: IStage) => validate(values, { application, pipeline });
};

// Reports only the fields the form changed. Stage edits made outside the form are kept, and
// a stage copy that went stale (e.g. one with defaults applied) can't overwrite newer values.
const getChangedValues = (values: IStage, prevValues: IStage): Partial<IStage> => {
  const changes: Partial<IStage> = {};
  new Set([...Object.keys(prevValues), ...Object.keys(values)]).forEach((key) => {
    if (values[key] !== prevValues[key]) {
      changes[key] = values[key];
    }
  });
  return changes;
};

export class FormikStageConfig extends React.Component<IFormikStageConfigProps> {
  public render() {
    const { render, onChange, stage, validate, application, pipeline } = this.props;
    return (
      <SpinFormik<IStage>
        validate={validate && decorate(validate, this.props)}
        initialValues={stage}
        onSubmit={() => {}}
        render={(formik) => (
          <LayoutProvider value={ResponsiveFieldLayout}>
            <WatchValue
              onChange={
                onChange && ((values: IStage, prevValues: IStage) => onChange(getChangedValues(values, prevValues)))
              }
              value={formik.values}
            />
            {render({ application, pipeline, formik })}
          </LayoutProvider>
        )}
      />
    );
  }
}
