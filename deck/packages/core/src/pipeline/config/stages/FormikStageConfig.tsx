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
  // The stage being edited. Pass the stage itself, not a copy, so edits made outside the form are kept.
  stage: IStage;
  // Values to show for fields the stage doesn't have yet. They are saved with the first edit.
  defaults?: Partial<IStage>;
  pipeline: IPipeline;
  validate?: IContextualValidator;
  render: (props: IFormikStageConfigInjectedProps) => React.ReactNode;
  onChange?: (values: IStage) => void;
}

export type IStageValidator = (values: IStage) => void | object | Promise<FormikErrors<IStage>>;

const decorate = (validate: IContextualValidator, props: IFormikStageConfigProps): IStageValidator => {
  const { application, pipeline } = props;
  return (values: IStage) => validate(values, { application, pipeline });
};

// Applies only the fields the form changed, so stage edits made outside the form are kept
const applyChangedValues = (stage: IStage, values: IStage, prevValues: IStage): IStage => {
  const changes: Partial<IStage> = {};
  new Set([...Object.keys(prevValues), ...Object.keys(values)]).forEach((key) => {
    if (values[key] !== prevValues[key]) {
      changes[key] = values[key];
    }
  });
  return { ...stage, ...changes };
};

export class FormikStageConfig extends React.Component<IFormikStageConfigProps> {
  // Without defaults, Formik starts from the stage itself, since some forms edit their values in place
  private initialValues: IStage = this.props.defaults
    ? { ...this.props.defaults, ...this.props.stage }
    : this.props.stage;

  private onValuesChanged = (values: IStage, prevValues: IStage) => {
    const { defaults, stage } = this.props;
    this.props.onChange(applyChangedValues({ ...defaults, ...stage }, values, prevValues));
  };

  public render() {
    const { render, onChange, validate, application, pipeline } = this.props;
    return (
      <SpinFormik<IStage>
        validate={validate && decorate(validate, this.props)}
        initialValues={this.initialValues}
        onSubmit={() => {}}
        render={(formik) => (
          <LayoutProvider value={ResponsiveFieldLayout}>
            <WatchValue onChange={onChange && this.onValuesChanged} value={formik.values} />
            {render({ application, pipeline, formik })}
          </LayoutProvider>
        )}
      />
    );
  }
}
