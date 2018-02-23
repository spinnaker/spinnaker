import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import { ICanaryConfig } from '../domain/ICanaryConfig';
import ConfigDetailActionButtons from './configDetailActionButtons';
import { mapStateToConfig } from '../service/canaryConfig.service';
import FormattedDate from '../layout/formattedDate';

interface IConfigDetailStateProps {
  selectedConfig: ICanaryConfig;
}

/*
 * Config detail header layout.
 */
function ConfigDetailHeader({ selectedConfig }: IConfigDetailStateProps) {
  return (
    <div className="horizontal config-detail-header">
      <div className="flex-3">
        <h1 className="heading-1 color-text-primary">Configuration{selectedConfig ? `: ${selectedConfig.name}` : ''}</h1>
      </div>
      <div className="flex-1">
        <h5 className="heading-5">
          <strong>Edited:</strong> <FormattedDate dateIso={selectedConfig ? selectedConfig.updatedTimestampIso : ''}/>
        </h5>
      </div>
      <div className="flex-2">
        <ConfigDetailActionButtons/>
      </div>
    </div>
  );
}

function mapStateToProps(state: ICanaryState): IConfigDetailStateProps {
  return {
    selectedConfig: mapStateToConfig(state),
  };
}

export default connect(mapStateToProps)(ConfigDetailHeader);
