import * as React from 'react';
import { Formik, FormikProps, FormikErrors } from 'formik';

import { IStage, IPipeline } from 'core/domain';
import { Application } from 'core/application';
import { LayoutProvider, ResponsiveFieldLayout, WatchValue } from 'core/presentation';

export interface IFormikStageConfigInjectedProps {
  application: Application;
  pipeline: IPipeline;
  formik: FormikProps<IStage>;
}

export type IContextualValidator = (
  values: IStage,
  context: any,
) => FormikErrors<IStage> | Promise<FormikErrors<IStage>>;

export interface IFormikStageConfigProps {
  application: Application;
  stage: IStage;
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

export class FormikStageConfig extends React.Component<IFormikStageConfigProps> {
  public render() {
    const { render, onChange, stage, validate, application, pipeline } = this.props;
    return (
      <Formik<IStage>
        validate={validate && decorate(validate, this.props)}
        initialValues={stage}
        onSubmit={() => {}}
        render={formik => (
          <LayoutProvider value={ResponsiveFieldLayout}>
            <WatchValue onChange={onChange} value={formik.values} />
            {render({ application, pipeline, formik })}
          </LayoutProvider>
        )}
      />
    );
  }
}
