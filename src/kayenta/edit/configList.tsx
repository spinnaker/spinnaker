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
    <section className="config-list">
      <ul className="tabs-vertical list-unstyled">
        {configs.map(config => (
          <li key={config.name} className={config.name === selectedConfigName ? 'selected' : ''}>
            <UISrefActive class="active">
              <UISref to=".configDetail" params={{configName: config.name, 'new': false, copy: false}}>
                <a className="heading-4">{config.name}</a>
              </UISref>
            </UISrefActive>
            <p className="body-small color-text-caption caption" style={{marginTop: '5px', marginBottom: '0'}}>
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
