import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { AnalysisType } from './AnalysisType';

export const KAYENTA_ANALYSIS_TYPE_COMPONENT = 'spinnaker.kayenta.analysisType.component';
module(KAYENTA_ANALYSIS_TYPE_COMPONENT, []).component(
  'kayentaAnalysisType',
  react2angular(withErrorBoundary(AnalysisType, 'kayentaAnalysisType'), ['analysisTypes', 'selectedType', 'onChange']),
);
