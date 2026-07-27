import { useSref } from '@uirouter/react';
import * as React from 'react';
import { connect } from 'react-redux';

import type { ICanaryState } from '../../reducers';
import { resolveConfigIdFromExecutionId } from '../../selectors';

interface IReportLinkOwnProps {
  configName: string;
  executionId: string;
  application: string;
  children?: React.ReactNode;
}

interface IReportLinkStateProps {
  configId: string;
}

export const ReportLink = ({ configId, executionId, children }: IReportLinkOwnProps & IReportLinkStateProps) => {
  const sref = useSref('^.reportDetail', { configId, runId: executionId });
  return <a {...sref}>{children}</a>;
};

const mapStateToProps = (
  state: ICanaryState,
  ownProps: IReportLinkOwnProps,
): IReportLinkStateProps & IReportLinkOwnProps => {
  return {
    configId: resolveConfigIdFromExecutionId(state, ownProps.executionId),
    ...ownProps,
  };
};

export default connect(mapStateToProps)(ReportLink);
