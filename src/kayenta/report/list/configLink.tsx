import { useSref } from '@uirouter/react';
import { ICanaryState } from 'kayenta/reducers';
import { resolveConfigIdFromExecutionId } from 'kayenta/selectors';
import * as React from 'react';
import { connect } from 'react-redux';

interface IConfigLinkOwnProps {
  configName: string;
  executionId: string;
  application: string;
}

interface IConfigLinkStateProps {
  configId: string;
}

export const ConfigLink = ({ configId, configName }: IConfigLinkOwnProps & IConfigLinkStateProps) => {
  const sref = useSref('^.^.canaryConfig.configDetail', { id: configId });
  return <a {...sref}>{configName}</a>;
};

const mapStateToProps = (
  state: ICanaryState,
  ownProps: IConfigLinkOwnProps,
): IConfigLinkStateProps & IConfigLinkOwnProps => {
  return {
    configId: resolveConfigIdFromExecutionId(state, ownProps.executionId),
    ...ownProps,
  };
};

export default connect(mapStateToProps)(ConfigLink);
