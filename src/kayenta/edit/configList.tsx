import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';

interface IConfigListStateProps {
  configs: ICanaryConfigSummary[];
}

interface IConfigListDispatchProps {
  selectConfig: any;
}

/*
 * Shows a list of available configurations the user can select for editing.
 */
function ConfigList({ configs, selectConfig }: IConfigListStateProps & IConfigListDispatchProps) {
  return (
    <section>
      <h2>Configs</h2>
      <ul className="list-group">
        {configs.map(config => (
          <li key={config.name} className="list-group-item">
            <a data-name={config.name} onClick={selectConfig}>{config.name}</a>
          </li>
        ))}
      </ul>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IConfigListStateProps {
  return {
    configs: state.configSummaries
  };
}

function mapDispatchToProps(dispatch: any): IConfigListDispatchProps {
  return {
    selectConfig: (event: any) => dispatch({
      type: 'load_config',
      id: event.target.dataset.name
    })
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(ConfigList);
