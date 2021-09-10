import type { FormikProps } from 'formik';
import { get } from 'lodash';
import React from 'react';

import { FormikFormField, HelpField, NumberInput, ReactSelectInput, TextInput, Tooltip } from '@spinnaker/core';
import type { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

import './InstancesDistribution.less';

export interface IInstancesDistributionProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
}

function useDefaultFormikValue(formik: FormikProps<IAmazonServerGroupCommand>, field: string, defaultValue: any) {
  const value = get(formik.values, field);
  React.useEffect(() => {
    if (value === undefined && defaultValue !== undefined) {
      formik.setFieldValue(field, defaultValue);
    }
  }, [field, defaultValue, value]);
}

export function InstancesDistribution(props: IInstancesDistributionProps) {
  const { values: command, setFieldValue } = props.formik;

  const spotAllocStrategyOptions = [
    { label: 'capacity-optimized (recommended)', value: 'capacity-optimized' },
    { label: 'capacity-optimized-prioritized', value: 'capacity-optimized-prioritized' },
    { label: 'lowest-price', value: 'lowest-price' },
  ];

  // When allocation strategy toggles to/from lowest-price, update spotInstancePools
  React.useEffect(() => {
    if (command.spotAllocationStrategy !== 'lowest-price') {
      setFieldValue('spotInstancePools', undefined);
    } else if (command.spotInstancePools === undefined) {
      setFieldValue('spotInstancePools', 2);
    }
  }, [command.spotAllocationStrategy]);

  useDefaultFormikValue(props.formik, 'spotAllocationStrategy', 'capacity-optimized');

  // prioritized is the only supported strategy for now
  useDefaultFormikValue(props.formik, 'onDemandAllocationStrategy', 'prioritized');

  // AWS defaults
  useDefaultFormikValue(props.formik, 'onDemandBaseCapacity', 0);
  useDefaultFormikValue(props.formik, 'onDemandPercentageAboveBaseCapacity', 100);

  return (
    <div className={'InstancesDistribution row sub-section form-group'}>
      <h4>Instances Distribution</h4>
      <div className={'description'}>
        Diversify and distribute instance types across purchase options.{' '}
        <HelpField id={'aws.serverGroup.instancesDistribution'} />
      </div>
      <br />

      <FormikFormField
        label={'Spot Allocation Strategy'}
        name={'spotAllocationStrategy'}
        help={<HelpField id={'aws.serverGroup.spotAllocationStrategy'} />}
        input={(inputProps) => <ReactSelectInput {...inputProps} mode="PLAIN" options={spotAllocStrategyOptions} />}
      />

      {props.formik.values.spotAllocationStrategy === 'lowest-price' && (
        <FormikFormField
          label={'Spot Instance Pools Count'}
          name={'spotInstancePools'}
          help={<HelpField id={'aws.serverGroup.spotInstancePoolCount'} />}
          input={(inputProps) => <NumberInput {...inputProps} />}
        />
      )}

      <FormikFormField
        label={'On-Demand Allocation Strategy'}
        name={'onDemandAllocationStrategy'}
        help={<HelpField id={'aws.serverGroup.odAllocationStrategy'} />}
        input={(inputProps) => <TextInput {...inputProps} disabled={true} />}
      />

      <FormikFormField
        label={'On-Demand Base Capacity'}
        name={'onDemandBaseCapacity'}
        help={<HelpField id={'aws.serverGroup.odBase'} />}
        input={(inputProps) => <NumberInput {...inputProps} />}
      />

      <FormikFormField
        label={'On-Demand Percentage Above Base Capacity'}
        name={'onDemandPercentageAboveBaseCapacity'}
        help={<HelpField id={'aws.serverGroup.odPercentAboveBase'} />}
        input={(inputProps) => <NumberInput {...inputProps} />}
      />

      <FormikFormField
        label={'Spot Max Price'}
        name={'spotPrice'}
        help={<HelpField id={'aws.serverGroup.spotMaxPrice'} />}
        input={(inputProps) => (
          <Tooltip value={'Recommended to leave empty and use AWS default i.e. On-Demand price'}>
            <TextInput {...inputProps} />
          </Tooltip>
        )}
      />
    </div>
  );
}
