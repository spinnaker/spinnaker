import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from 'kayenta/reducers';
import { resolveConfigIdFromNameAndApplication } from 'kayenta/selectors';
import { Link } from './link';

interface IConfigLinkOwnProps {
  configName: string;
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
    configId: resolveConfigIdFromNameAndApplication(state, ownProps.configName, ownProps.application),
    ...ownProps,
  };
};

export default connect(mapStateToProps)(ConfigLink);
