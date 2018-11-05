import { module } from 'angular';
import { react2angular } from 'react2angular';
import { AnalysisType } from './AnalysisType';

export const KAYENTA_ANALYSIS_TYPE_COMPONENT = 'spinnaker.kayenta.analysisType.component';
module(KAYENTA_ANALYSIS_TYPE_COMPONENT, []).component(
  'kayentaAnalysisType',
  react2angular(AnalysisType, ['analysisTypes', 'selectedType', 'onChange']),
);
