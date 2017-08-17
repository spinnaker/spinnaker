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
    <div className="row">
      <div className="col-sm-6">
        <h2>{selectedConfig ? selectedConfig.name : ''}</h2>
      </div>
      <div className="col-sm-3">
        <strong>Edited:</strong> <FormattedDate dateIso={selectedConfig ? selectedConfig.updatedTimestampIso : ''}/>
      </div>
      <div className="col-sm-3">
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
