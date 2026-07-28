import * as React from 'react';

import { vizConfig } from './config';
import './noValidDataSign.less';

export default () => (
  <div style={{ height: vizConfig.height }} className="no-data-sign">
    No Valid Data is Available for This Metric
  </div>
);
