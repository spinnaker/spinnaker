import React from 'react';
import { useData } from '@spinnaker/core';
import { VpcReader } from './VpcReader';

export interface IVpcTagProps {
  vpcId: string;
}

const defaultLabel = 'None (EC2 Classic)';

export function VpcTag(props: IVpcTagProps) {
  const { vpcId } = props;
  const fetchVpcLabel = useData(
    async () => {
      const name = await VpcReader.getVpcName(props.vpcId);
      return name ? `${name} (${props.vpcId})` : `(${props.vpcId})`;
    },
    defaultLabel,
    [vpcId],
  );

  const label = vpcId ? fetchVpcLabel.result : defaultLabel;
  return <span className="vpc-tag">{label}</span>;
}
