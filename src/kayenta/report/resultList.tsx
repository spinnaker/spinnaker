import * as React from 'react';
import { connect } from 'react-redux';
import CenteredDetail from '../layout/centeredDetail';

const ResultList = () => (
  <CenteredDetail>
    <h3 className="heading-3">
      Canary report explorer not yet implemented.
    </h3>
  </CenteredDetail>
);

export default connect()(ResultList);
