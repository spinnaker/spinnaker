import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';

interface IConfigListProps {
  configs: ICanaryConfigSummary[];
}

/*
 * Shows a list of available configurations the user can select for editing.
 */
function ConfigList({ configs }: IConfigListProps) {
  return (
    <section>
      <h2>Configs</h2>
      <ul>
        {configs.map(config => (
          <li key={config.name}>
            {config.name}
          </li>
        ))}
      </ul>
    </section>
  );
}

function mapStateToProps(state: any): IConfigListProps {
  return {
    configs: state.configSummaries
  };
}

export default connect(mapStateToProps)(ConfigList);
