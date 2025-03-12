// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { uniqBy } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';

import type { IFormikStageConfigInjectedProps, IFormInputProps, ISecurityGroup, ISubnet, IVpc } from '@spinnaker/core';
import {
  FormikFormField,
  NetworkReader,
  ReactInjector,
  ReactSelectInput,
  SubnetReader,
  TetheredSelect,
  useData,
} from '@spinnaker/core';

const toSubnetOption = (value: ISubnet): Option<string> => {
  return { value: value.id, label: value.id };
};

export function NetworkForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;

  const onChangeVpc = (vpcs: any) => {
    props.formik.setFieldValue('securityGroupIds', null);
    props.formik.setFieldValue('subnetIds', null);
    props.formik.setFieldValue('vpcId', vpcs.target.value);
  };

  const onChangeSubnet = (subnets: any) => {
    const subnetsSelected = subnets.map((o: any) => o.value);
    props.formik.setFieldValue('subnetIds', subnetsSelected);
  };

  const onChangeSG = (sgs: any) => {
    const sgsSelected = sgs.map((o: any) => o.value);
    props.formik.setFieldValue('securityGroupIds', sgsSelected);
  };

  const { result: fetchVpcsResult, status: fetchVpcsStatus } = useData(
    () => NetworkReader.listNetworksByProvider('aws'),
    [],
    [],
  );

  const { result: fetchSubnetsResult } = useData(() => SubnetReader.listSubnetsByProvider('aws'), [], []);

  const { result: fetchSGsResult } = useData(
    () => ReactInjector.securityGroupReader.getAllSecurityGroups(),
    undefined,
    [],
  );

  const availableVpcs =
    values.account && values.region && fetchVpcsStatus !== 'PENDING'
      ? fetchVpcsResult
          .filter((v: IVpc) => v.deprecated === false)
          .filter((v: IVpc) => v.account === values.account)
          .filter((v: IVpc) => v.region === values.region)
          .map((v: IVpc) => v.id)
      : [];

  const dedupedSubnets = uniqBy(
    fetchSubnetsResult.filter((s: ISubnet) => s.vpcId === values.vpcId),
    'id',
  );

  const availableSGs =
    values.account && values.region && values.vpcId
      ? fetchSGsResult[values.account]['aws'][values.region]
          .filter((sg: ISecurityGroup) => sg.vpcId === values.vpcId)
          .map((sg: ISecurityGroup) => ({ value: sg.id, label: sg.name }))
      : [];

  return (
    <div>
      <FormikFormField
        name="vpcId"
        label="VPC Id"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            onChange={onChangeVpc}
            isLoading={fetchVpcsStatus === 'PENDING'}
            stringOptions={availableVpcs}
            clearable={true}
          />
        )}
      />
      <div className="form-group">
        <div className="col-md-4 sm-label-right">
          <b>Subnets </b>
        </div>
        {dedupedSubnets.length === 0 ? (
          <div className="form-control-static">No subnets found in the selected account/region/VPC </div>
        ) : (
          <div className="col-md-6">
            <TetheredSelect
              multi={true}
              options={dedupedSubnets.map((s: ISubnet) => toSubnetOption(s))}
              value={values.subnetIds}
              onChange={onChangeSubnet}
            />
          </div>
        )}
      </div>
      <div className="form-group">
        <div className="col-md-4 sm-label-right">
          <b>Security Groups </b>
        </div>
        {availableSGs.length === 0 ? (
          <div className="form-control-static">No security groups found in the selected account/region/VPC </div>
        ) : (
          <div className="col-md-6">
            <TetheredSelect multi={true} options={availableSGs} value={values.securityGroupIds} onChange={onChangeSG} />
          </div>
        )}
      </div>
    </div>
  );
}
