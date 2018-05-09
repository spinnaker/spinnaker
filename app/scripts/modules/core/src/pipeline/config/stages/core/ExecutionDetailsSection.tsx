import * as React from 'react';

import { Application } from 'core/application/application.model';
import { IExecution } from 'core/domain/IExecution';
import { IExecutionStage } from 'core/domain/IExecutionStage';
import { IExecutionDetailsSection } from 'core/domain/IStageTypeConfig';

export interface IExecutionDetailsSectionProps extends IExecutionDetailsSectionWrapperProps {
  // NOTE: these are the fields from IExecutionDetailsComponentProps, but TS is not behaving in the library build,
  // and does not recognize fields in a separate interface that this one extends when they are in a separate file, so
  // we are copying them here
  application: Application;
  detailsSections: IExecutionDetailsSection[];
  execution: IExecution;
  provider: string;
  stage: IExecutionStage;
}

export interface IExecutionDetailsSectionWrapperProps {
  children?: any;
  name: string;
  current: string;
}

export const ExecutionDetailsSection: React.StatelessComponent<IExecutionDetailsSectionWrapperProps> = (
  props: IExecutionDetailsSectionWrapperProps,
): JSX.Element => {
  if (props.current === props.name) {
    return <div className="step-section-details">{props.children}</div>;
  }
  return null;
};
