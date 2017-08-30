import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { UISref, UISrefActive } from '@uirouter/react';
import CreateConfigButton from './createConfigButton';
import FormattedDate from '../layout/formattedDate';

interface IConfigListStateProps {
  configs: ICanaryConfigSummary[];
  selectedConfigName: string;
}

/*
 * Shows a list of available configurations the user can select for editing.
 */
function ConfigList({ configs, selectedConfigName }: IConfigListStateProps) {
  return (
    <section>
      <h2>Configs</h2>
      <ul className="tabs-vertical list-unstyled">
        {configs.map(config => (
          <li key={config.name} className={config.name === selectedConfigName ? 'selected' : ''}>
            <UISrefActive class="active">
              <UISref to=".configDetail" params={{configName: config.name}}>
                <a><strong>{config.name}</strong></a>
              </UISref>
            </UISrefActive>
            <p>
              Edited: <FormattedDate dateIso={config.updatedTimestampIso}/>
            </p>
          </li>
        ))}
      </ul>
      <CreateConfigButton/>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IConfigListStateProps {
  const selectedConfigName = state.selectedConfig.config ? state.selectedConfig.config.name : null;
  return {
    selectedConfigName,
    configs: state.data.configSummaries
  };
}


export default connect(mapStateToProps)(ConfigList);
