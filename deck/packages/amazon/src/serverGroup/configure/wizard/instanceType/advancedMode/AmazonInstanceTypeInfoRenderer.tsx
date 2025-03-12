import _ from 'lodash';
import React from 'react';

import type { IAmazonInstanceType } from '../../../../../instance/awsInstanceType.service';

export function AmazonInstanceTypeInfoRenderer(props: { instanceType: IAmazonInstanceType }) {
  const spotSupport = props.instanceType?.supportedUsageClasses?.includes('spot') ? '| SPOT supported' : '';
  const CpuMem = `${props.instanceType?.defaultVCpus} vCPU | ${props.instanceType?.memoryInGiB} Gib Memory ${spotSupport}`;
  const instanceStorageInfo = props.instanceType?.instanceStorageSupported && (
    <span>
      <br />
      <span className={`select-option-label-attributes`}>
        {`Instance Storage: ${_.toUpper(props.instanceType?.instanceStorageInfo?.storageTypes)} | ${
          props.instanceType?.instanceStorageInfo?.totalSizeInGB
        } Gib total size`}
      </span>
    </span>
  );
  const ebsInfo = props.instanceType?.ebsInfo && (
    <span>
      <br />
      <span className={`select-option-label-attributes`}>
        {`EBS: optimization ${props.instanceType?.ebsInfo?.ebsOptimizedSupport} | NVMe ${props.instanceType?.ebsInfo?.nvmeSupport} | Encryption ${props.instanceType?.ebsInfo?.encryptionSupport}`}
      </span>
    </span>
  );
  const gpuInfo = props.instanceType?.gpuInfo && (
    <span>
      <br />
      <span className={`select-option-label-attributes`}>
        {`GPU: ${props.instanceType?.gpuInfo?.totalGpuMemoryInMiB} MiB total memory`}
        {props.instanceType?.gpuInfo?.gpus.map(
          (g) => ` | ${g.count} ${g.manufacturer} ${g.name} GPUs, size: ${g.gpuSizeInMiB} MiB`,
        )}
      </span>
    </span>
  );
  const generationInfo = typeof props.instanceType?.currentGeneration !== 'undefined' &&
    props.instanceType?.currentGeneration !== null && (
      <span>
        <br />
        <span className={`select-option-label-attributes`}>
          {props.instanceType?.currentGeneration ? 'Current Generation' : 'Previous Generation'}
        </span>
      </span>
    );

  return (
    <span>
      <span className={`select-option-label`}>{props.instanceType?.name}</span>
      <br />
      <span className={`select-option-label-attributes`}>{CpuMem}</span>
      {instanceStorageInfo}
      {ebsInfo}
      {gpuInfo}
      {generationInfo}
    </span>
  );
}
