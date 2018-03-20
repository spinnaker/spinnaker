import * as React from 'react';
import { connect } from 'react-redux';
import { SETTINGS } from '@spinnaker/core';
import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryState } from 'kayenta/reducers';
import { runSelector, serializedCanaryConfigSelector } from 'kayenta/selectors';
import { ICanaryExecutionStatusResult } from 'kayenta/domain';

interface ISourceJsonStateProps {
  reportUrl: string;
  metricListUrl: string;
}

const SourceLinks = ({ reportUrl, metricListUrl }: ISourceJsonStateProps) => {
  return (
    <ul className="list-unstyled small">
      <li>
        <a target="_blank" href={reportUrl}>Report</a>
      </li>
      <li>
        <p><a target="_blank" href={metricListUrl}>Metrics</a></p>
      </li>
    </ul>
  );
};

const resolveReportUrl = (state: ICanaryState): string => {
  const canaryConfigId = serializedCanaryConfigSelector(state).id;
  const result: ICanaryExecutionStatusResult = runSelector(state);
  const canaryRunId = result.id;
  let url = `${SETTINGS.gateUrl}/v2/canaries/canary/${canaryConfigId}/${canaryRunId}`;
  const storageAccountName = result.storageAccountName || CanarySettings.storageAccountName;
  if (storageAccountName) {
    url += `?storageAccountName=${storageAccountName}`;
  }
  return url;
};

const resolveMetricListUrl = (state: ICanaryState): string => {
  const status = runSelector(state);
  const metricSetPairListId = status.metricSetPairListId || status.result.metricSetPairListId;
  let url = `${SETTINGS.gateUrl}/v2/canaries/metricSetPairList/${metricSetPairListId}`;
  const storageAccountName = status.storageAccountName || CanarySettings.storageAccountName;
  if (storageAccountName) {
    url += `?storageAccountName=${storageAccountName}`;
  }
  return url;
};

const mapStateToProps = (state: ICanaryState): ISourceJsonStateProps => {
  return {
    reportUrl: resolveReportUrl(state),
    metricListUrl: resolveMetricListUrl(state),
  };
};

export default connect(mapStateToProps)(SourceLinks);
