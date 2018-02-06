import * as React from 'react';
import CenteredDetail from 'kayenta/layout/centeredDetail';
import { CanarySettings } from 'kayenta/canary.settings';
import ExecutionListLoadStates from './loadStates';

const ExecutionList = () => {
  if (CanarySettings.executionListEnabled) {
    return <ExecutionListLoadStates/>;
  } else {
    return (
      <CenteredDetail>
        <h3 className="heading-3">
          Canary report explorer not yet implemented.
        </h3>
      </CenteredDetail>
    );
  }
};

export default ExecutionList;
