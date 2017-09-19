import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import * as Creators from '../actions/creators';
import { ICanaryState } from '../reducers/index';
import { ICanaryConfig } from '../domain/ICanaryConfig';
import { mapStateToConfig } from '../service/canaryConfig.service';

interface ICopyConfigButtonStateProps {
  config: string;
  disabled: boolean;
}

interface ICopyConfigButtonDispatchProps {
  copyConfig: (event: any) => void;
}

/*
 * Button for copying a canary config.
 */
function CopyConfigButton({ copyConfig, config, disabled }: ICopyConfigButtonDispatchProps & ICopyConfigButtonStateProps) {
  return (
    <button className="passive" data-config={config} disabled={disabled} onClick={copyConfig}>Copy</button>
  );
}

function mapStateToProps(state: ICanaryState) {
  return {
    config: JSON.stringify(buildConfigCopy(state)),
    disabled: state.selectedConfig.config && state.selectedConfig.config.isNew,
  }
}

function buildConfigCopy(state: ICanaryState): ICanaryConfig {
  const config = mapStateToConfig(state);
  if (!config) {
    return null;
  }

  // Probably a rare case, but someone could be lazy about naming their configs.
  let configName = `${config.name}-copy`, i = 1;
  while ((state.data.configSummaries || []).find(summary => summary.name === configName)) {
    configName = `${config.name}-copy-${i}`;
    i++;
  }

  config.name = configName;
  config.isNew = true;
  return config;
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): ICopyConfigButtonDispatchProps {
  return {
    copyConfig: (event: any) => {
      dispatch(Creators.selectConfig({ config: JSON.parse(event.target.dataset.config) }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(CopyConfigButton);
