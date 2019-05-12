import * as React from 'react';
import { connect } from 'react-redux';
import { CopyToClipboard } from '@spinnaker/core';

import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryState } from 'kayenta/reducers';
import { selectedMetricConfigSelector } from 'kayenta/selectors';
import metricStoreConfigStore from 'kayenta/metricStore/metricStoreConfig.service';
import './metricResultActions.less';

export interface IMetricResultStatsStateProps {
  metricConfig: ICanaryMetricConfig;
  metricSetPair: IMetricSetPair;
}

const buildAtlasGraphUrl = (metricSetPair: IMetricSetPair) => {
  const { attributes, scopes, values } = metricSetPair;
  const { atlasGraphBaseUrl } = CanarySettings;

  // TODO: If the control and experiment have different baseURLs, generate two links instead of a combined one.
  const backend = encodeURIComponent(attributes.control.baseURL);
  const query = `${attributes.experiment.query},Canary,:legend,:freeze,${attributes.control.query},Baseline,:legend`;

  const startTime = Math.min(scopes.control.startTimeMillis, scopes.experiment.startTimeMillis);
  const controlEndTime = scopes.control.startTimeMillis + values.control.length * scopes.control.stepMillis;
  const experimentEndTime = scopes.experiment.startTimeMillis + values.experiment.length * scopes.experiment.stepMillis;
  const endTime = Math.max(controlEndTime, experimentEndTime);

  return `${atlasGraphBaseUrl}?backend=${backend}&g.q=${query}&g.s=${startTime}&g.e=${endTime}&g.w=651&mode=png&axis=0`;
};

const MetricResultActions = ({ metricSetPair, metricConfig }: IMetricResultStatsStateProps) => {
  const atlasURL = buildAtlasGraphUrl(metricSetPair);
  const atlasQuery = metricStoreConfigStore.getDelegate(metricConfig.query.type).queryFinder(metricConfig);

  // Mask CopyToClipboard component as a larger button, fire event when clicked
  const copyToClipboard = (
    <div className="copy-button-container">
      <CopyToClipboard displayText={false} text={atlasQuery} toolTip={null} />
      <button className="primary copy-button" key="copy-link">
        <i className="glyphicon glyphicon-copy  copy-icon" />
        Copy this Metric URL
      </button>
    </div>
  );

  const openAtlas = (
    <a href={atlasURL} target="_blank">
      <button className="primary">
        <i className="fas fa-chart-line" />
        Explore More Data in Atlas
      </button>
    </a>
  );

  const actions = [copyToClipboard, openAtlas].map((action, i) => {
    return (
      <li className="action" key={i}>
        {action}
      </li>
    );
  });

  return (
    <div className="metric-result-actions">
      <ul className="actions-layout list-inline">{actions}</ul>
    </div>
  );
};

const mapStateToProps = (state: ICanaryState): IMetricResultStatsStateProps => ({
  metricConfig: selectedMetricConfigSelector(state),
  metricSetPair: state.selectedRun.metricSetPair.pair,
});

export default connect(mapStateToProps)(MetricResultActions);
