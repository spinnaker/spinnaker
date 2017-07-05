import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { UISref, UISrefActive } from '@uirouter/react';

interface IConfigListStateProps {
  configs: ICanaryConfigSummary[];
}

/*
 * Shows a list of available configurations the user can select for editing.
 */
function ConfigList({ configs }: IConfigListStateProps) {
  return (
    <section>
      <h2>Configs</h2>
      <ul className="list-group">
        {configs.map(config => (
          <li key={config.name} className="list-group-item">
            <UISrefActive class="active">
              <UISref to=".configDetail" params={{configName: config.name}}>
                <a>{config.name}</a>
              </UISref>
            </UISrefActive>
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


export default connect(mapStateToProps)(ConfigList);
