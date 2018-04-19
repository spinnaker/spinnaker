import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from 'kayenta/reducers';
import { resolveConfigIdFromExecutionId } from 'kayenta/selectors';
import { Link } from './link';

interface IConfigLinkOwnProps {
  configName: string;
  executionId: string;
  application: string;
}

interface IConfigLinkStateProps {
  configId: string;
}

export const ConfigLink = ({ configId, configName }: IConfigLinkOwnProps & IConfigLinkStateProps) => {
  return (
    <Link
      targetState="^.^.canaryConfig.configDetail"
      stateParams={{
        id: configId,
      }}
      linkText={configName}
    />
  );
};

const mapStateToProps = (state: ICanaryState, ownProps: IConfigLinkOwnProps): IConfigLinkStateProps & IConfigLinkOwnProps => {
  return {
    configId: resolveConfigIdFromExecutionId(state, ownProps.executionId),
    ...ownProps,
  };
};

export default connect(mapStateToProps)(ConfigLink);
