import { flatMap, uniq } from 'lodash';
import * as React from 'react';
import type { Subject } from 'rxjs';

import type { IMetricAlarmDimension, IServerGroup } from '@spinnaker/core';
import { CloudMetricsReader, ReactSelectInput, TextInput, useData } from '@spinnaker/core';

import type { IScalingPolicyAlarmView } from '../../../../../domain';

import './dimensionsEditor.less';

export interface IDimensionsEditorProps {
  alarm: IScalingPolicyAlarmView;
  namespaceUpdated?: Subject<void>;
  serverGroup: IServerGroup;
  updateAvailableMetrics: (dimensions: IMetricAlarmDimension[]) => void;
}

export const DimensionsEditor = ({ alarm, serverGroup, updateAvailableMetrics }: IDimensionsEditorProps) => {
  const dimensions = alarm.dimensions || [];

  const fetchDimensions = () => {
    return CloudMetricsReader.listMetrics('aws', serverGroup.account, serverGroup.region, {
      namespace: alarm.namespace,
    })
      .then((results) => {
        const allDimensions = flatMap(results, (r) => r.dimensions);
        const sortedDimensions = uniq(allDimensions.filter((d) => d).map((d) => d.name)).sort();
        return sortedDimensions;
      })
      .catch(() => {
        return [];
      });
  };
  const { result: dimensionOptions } = useData(fetchDimensions, [], [alarm.namespace, serverGroup.name]);

  const addDimension = () => {
    const newDimensions = [...dimensions, {} as IMetricAlarmDimension];
    updateAvailableMetrics(newDimensions);
  };
  const removeDimension = (index: number) => {
    const newDimensions = alarm.dimensions.filter((_d, i) => i !== index);
    updateAvailableMetrics(newDimensions);
  };
  const updateDimension = (key: 'name' | 'value', value: string, index: number) => {
    const newDimensions = [...dimensions];
    const updatedDimension = newDimensions[index];
    updatedDimension[key] = value;
    updateAvailableMetrics(newDimensions);
  };
  return (
    <div>
      <div className="row">
        <div className="col-md-12 small">
          <h5>Dimensions</h5>
        </div>
      </div>
      {dimensions.map((dimension, i) => (
        <div key={`dimension-${i}`} className="row dimensions-row horizontal middle">
          <div className="col-md-6">
            <ReactSelectInput
              onChange={(e) => updateDimension('name', e.target.value, i)}
              value={dimension.name}
              stringOptions={dimensionOptions}
            />
          </div>
          <TextInput onChange={(e) => updateDimension('value', e.target.value, i)} value={dimension.value} />
          {!alarm.disableEditingDimensions && (
            <div className="col-md-1" onClick={() => removeDimension(i)}>
              <a>
                <i className="glyphicon glyphicon-trash clickable" />
              </a>
            </div>
          )}
        </div>
      ))}
      {!alarm.disableEditingDimensions && (
        <div className="row">
          <div>
            <button type="button" className="btn btn-block btn-xs add-new" onClick={addDimension}>
              <span className="glyphicon glyphicon-plus-sign sp-margin-xs-left"></span>
              Add dimension
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
