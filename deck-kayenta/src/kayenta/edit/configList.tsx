import { UISref } from '@uirouter/react';
import { ICanaryConfigSummary } from 'kayenta/domain/ICanaryConfigSummary';
import FormattedDate from 'kayenta/layout/formattedDate';
import { ICanaryState } from 'kayenta/reducers';
import { sortBy } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';

import CreateConfigButton from './createConfigButton';
import { OwnedBy } from './ownedBy';

import './configList.less';

interface IConfigListStateProps {
  configs: ICanaryConfigSummary[];
  selectedConfigId: string;
  application: string;
}

/*
 * Shows a list of available configurations the user can select for editing.
 */
function ConfigList({ configs, selectedConfigId, application }: IConfigListStateProps) {
  return (
    <section className="config-list">
      <ul className="tabs-vertical list-unstyled " style={{ wordBreak: 'break-all' }}>
        {configs.map((config) => (
          <li key={config.id} className={config.id === selectedConfigId ? 'selected' : ''}>
            <UISref to=".configDetail" params={{ id: config.id, new: false, copy: false }}>
              <a>
                <div className="heading-4 color-text-primary">{config.name}</div>
                <div className="body-small color-text-caption caption" style={{ marginTop: '5px', marginBottom: '0' }}>
                  Edited: <FormattedDate dateIso={config.updatedTimestampIso} />
                  <br />
                  <OwnedBy owningApplications={config.applications} currentApplication={application} />
                </div>
              </a>
            </UISref>
          </li>
        ))}
      </ul>
      <CreateConfigButton />
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IConfigListStateProps {
  const selectedConfigId = state.selectedConfig.config ? state.selectedConfig.config.id : null;
  const application = state.data.application.name;
  return {
    selectedConfigId,
    configs: sortBy(state.data.configSummaries, 'name'),
    application,
  };
}

export default connect(mapStateToProps)(ConfigList);
