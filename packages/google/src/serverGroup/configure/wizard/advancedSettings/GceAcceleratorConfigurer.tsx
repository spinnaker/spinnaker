import { module } from 'angular';

import { get, isEmpty } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';
import { react2angular } from 'react2angular';

import { HelpField, withErrorBoundary } from '@spinnaker/core';

import { IGceAcceleratorType } from './gceAccelerator.service';

interface IGceAcceleratorConfig {
  acceleratorType: string;
  acceleratorCount: number;
}

export interface IGceAcceleratorConfigurerProps {
  acceleratorConfigs: IGceAcceleratorConfig[];
  availableAccelerators: IGceAcceleratorType[];
  regional: boolean;
  setAcceleratorConfigs: (configs: IGceAcceleratorConfig[]) => void;
  zone: string;
}

export function GceAcceleratorConfigurer({
  acceleratorConfigs = [],
  availableAccelerators = [],
  regional,
  setAcceleratorConfigs,
  zone,
}: IGceAcceleratorConfigurerProps) {
  const addAccelerator = (): void => {
    if (isEmpty(availableAccelerators)) {
      return;
    }
    setAcceleratorConfigs(
      acceleratorConfigs.concat([
        {
          acceleratorType: availableAccelerators[0].name,
          acceleratorCount: 1,
        },
      ]),
    );
  };

  const removeAccelerator = (indexToRemove: number): void => {
    setAcceleratorConfigs(
      acceleratorConfigs.filter((_config, index) => {
        return indexToRemove !== index;
      }),
    );
  };

  const getTypeOptions = (): Array<Option<string>> => {
    return availableAccelerators.map(({ description, name }) => {
      return {
        label: description,
        value: name,
      };
    });
  };

  const getAvailableCardCounts = (acceleratorType: string): number[] => {
    const acceleratorConfig = availableAccelerators.find(({ name }) => name === acceleratorType);
    return get(acceleratorConfig, 'availableCardCounts', []);
  };

  const getCountOptions = (acceleratorType: string): Array<Option<number>> => {
    return getAvailableCardCounts(acceleratorType).map((count) => ({
      label: count.toString(),
      value: count,
    }));
  };

  const getNearestCount = (type: string, currentCount: number): number => {
    const availableCounts = getAvailableCardCounts(type);
    if (availableCounts.includes(currentCount)) {
      return currentCount;
    }
    return availableCounts.reduce((nearest, count) => {
      if (count > currentCount) {
        return nearest;
      }
      return count;
    }, 1);
  };

  const onTypeChange = (indexToUpdate: number, type: string): void => {
    setAcceleratorConfigs(
      acceleratorConfigs.map((config, index) => {
        if (indexToUpdate !== index) {
          return config;
        }
        return {
          acceleratorType: type,
          acceleratorCount: getNearestCount(type, config.acceleratorCount),
        };
      }),
    );
  };

  const onCountChange = (indexToUpdate: number, count: number) => {
    setAcceleratorConfigs(
      acceleratorConfigs.map((config, index) => {
        if (indexToUpdate !== index) {
          return config;
        }
        return {
          ...config,
          acceleratorCount: count,
        };
      }),
    );
  };

  return (
    <div className="form-group">
      <div className="sm-label-left">
        Accelerators <HelpField id="gce.serverGroup.accelerator" />
      </div>
      <table className="table table-condensed packed tags">
        <thead>
          <tr>
            <th style={{ width: '75%' }}>Type</th>
            <th style={{ width: '15%' }}>Count</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {acceleratorConfigs.map(({ acceleratorType, acceleratorCount }, i) => (
            <tr key={i}>
              <td>
                <Select
                  clearable={false}
                  onChange={(option: Option<string>) => onTypeChange(i, option.value)}
                  options={getTypeOptions()}
                  value={acceleratorType}
                />
              </td>
              <td>
                <Select
                  clearable={false}
                  onChange={(option: Option<number>) => onCountChange(i, option.value)}
                  options={getCountOptions(acceleratorType)}
                  value={acceleratorCount}
                />
              </td>
              <td>
                <button className="btn btn-link" onClick={() => removeAccelerator(i)}>
                  <span className="glyphicon glyphicon-trash" />
                </button>
              </td>
            </tr>
          ))}
          {!isEmpty(acceleratorConfigs) && (
            <tr>
              <td colSpan={3}>
                Adding Accelerators places constraints on the instances that you can deploy. See
                <a href="https://cloud.google.com/compute/docs/gpus/#restrictions" target="_blank">
                  {' '}
                  the complete list of these restrictions
                </a>{' '}
                for more information.
              </td>
            </tr>
          )}
          {!isEmpty(availableAccelerators) && (
            <tr>
              <td colSpan={3}>
                <button className="btn btn-block btn-sm add-new" onClick={addAccelerator}>
                  <span className="glyphicon glyphicon-plus-sign" /> Add Accelerator
                </button>
              </td>
            </tr>
          )}
          {isEmpty(availableAccelerators) && !regional && !zone && (
            <tr>
              <td colSpan={3}>
                A zone must be selected to configure accelerators. Please note: the set of available accelerator types
                are limited by zone. See{' '}
                <a href="https://cloud.google.com/compute/docs/gpus/#gpus-list" target="_blank">
                  the complete list of types in each zone
                </a>{' '}
                for more information.
              </td>
            </tr>
          )}
          {isEmpty(availableAccelerators) && !regional && zone && (
            <tr>
              <td colSpan={3}>There are no accelerators available in the currently selected zone.</td>
            </tr>
          )}
          {isEmpty(availableAccelerators) && regional && (
            <tr>
              <td colSpan={3}>
                There are no accelerators available in all of the currently selected zone(s). Please explicitly select
                only zones that each support the accelerators you would like to configure. See{' '}
                <a href="https://cloud.google.com/compute/docs/gpus/#gpus-list" target="_blank">
                  the complete list of types in each zone
                </a>{' '}
                for more information.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export const GCE_ACCELERATOR_CONFIGURER = 'spinnaker.gce.accelerator.component';
module(GCE_ACCELERATOR_CONFIGURER, []).component(
  'gceAcceleratorConfigurer',
  react2angular(withErrorBoundary(GceAcceleratorConfigurer, 'gceAcceleratorConfigurer'), [
    'acceleratorConfigs',
    'availableAccelerators',
    'regional',
    'setAcceleratorConfigs',
    'zone',
  ]),
);
