import React from 'react';

import type { ICustomInstanceBuilderProps } from '@spinnaker/core';
import { useDeckRuntimeServices } from '@spinnaker/core';

import type { ICustomInstanceConfig } from './CustomInstanceConfigurer';
import { CustomInstanceConfigurer } from './CustomInstanceConfigurer';
import { GceCustomInstanceBuilderService } from '../../../../instance/custom/customInstanceBuilder.gce.service';

const customInstanceBuilderService = new GceCustomInstanceBuilderService();

export function GceCustomInstanceBuilder({ command, onTypeChanged }: ICustomInstanceBuilderProps) {
  const { instanceTypeService } = useDeckRuntimeServices();
  const gceCommand = command as any;
  const [config, setConfig] = React.useState<ICustomInstanceConfig>(() => getInitialConfig(gceCommand));
  const choices = getChoices(gceCommand, config);
  const effectiveConfig = withValidDefaults(config, choices);

  const handleChange = (nextConfig: ICustomInstanceConfig) => {
    const nextChoices = getChoices(gceCommand, nextConfig);
    const completeConfig = withValidDefaults(nextConfig, nextChoices);
    const nextInstanceType = customInstanceBuilderService.generateInstanceTypeString(
      completeConfig.instanceFamily,
      completeConfig.vCpuCount,
      completeConfig.memory,
      completeConfig.extendedMemory,
    );
    gceCommand.viewState.customInstance = completeConfig;
    gceCommand.instanceType = nextInstanceType;
    gceCommand.customInstanceChanged && gceCommand.customInstanceChanged(gceCommand);
    setConfig(completeConfig);
    instanceTypeService
      .getInstanceTypeDetails(gceCommand.selectedProvider, nextInstanceType)
      .then((instanceTypeDetails) => {
        gceCommand.viewState.instanceTypeDetails = instanceTypeDetails;
      });
    onTypeChanged && onTypeChanged(nextInstanceType);
  };

  return (
    <CustomInstanceConfigurer
      instanceFamilyList={choices.instanceFamilyList}
      memoryList={choices.memoryList}
      onChange={handleChange}
      selectedExtendedMemory={effectiveConfig.extendedMemory}
      selectedInstanceFamily={effectiveConfig.instanceFamily}
      selectedMemory={effectiveConfig.memory}
      selectedVCpuCount={effectiveConfig.vCpuCount}
      vCpuList={choices.vCpuList}
    />
  );
}

function getInitialConfig(command: any): ICustomInstanceConfig {
  if (command.viewState.customInstance) {
    return {
      extendedMemory: isExtendedMemory(command.instanceType),
      ...command.viewState.customInstance,
    };
  }

  const parsed = command.instanceType
    ? customInstanceBuilderService.parseInstanceTypeString(stripExtendedMemorySuffix(command.instanceType))
    : { instanceFamily: 'N1', vCpuCount: null, memory: null };

  const config = withValidDefaults(
    {
      ...parsed,
      extendedMemory: isExtendedMemory(command.instanceType),
    },
    getChoices(command, parsed as Partial<ICustomInstanceConfig>),
  );
  command.viewState.customInstance = config;
  return config;
}

function withValidDefaults(
  config: Partial<ICustomInstanceConfig>,
  choices: ReturnType<typeof getChoices>,
): ICustomInstanceConfig {
  const instanceFamily = config.instanceFamily || choices.instanceFamilyList[0] || 'N1';
  const vCpuCount = config.vCpuCount || choices.vCpuList[0];
  const memoryList = choices.memoryList.length
    ? choices.memoryList
    : vCpuCount
    ? customInstanceBuilderService.generateValidMemoryListForVCpuCount(instanceFamily, vCpuCount)
    : [];

  return {
    extendedMemory: Boolean(config.extendedMemory),
    instanceFamily,
    memory: config.memory || memoryList[0],
    vCpuCount,
  };
}

function getChoices(command: any, config: Partial<ICustomInstanceConfig>) {
  const customInstanceTypes = command.backingData.customInstanceTypes || {};
  const location = command.regional ? command.region : command.zone;
  const locationToInstanceTypesMap =
    command.backingData.credentialsKeyedByAccount?.[command.credentials]?.locationToInstanceTypesMap;
  const vCpuList =
    customInstanceTypes.vCpuList ||
    (location && locationToInstanceTypesMap
      ? customInstanceBuilderService.generateValidVCpuListForLocation(location, locationToInstanceTypesMap)
      : []);
  const selectedVCpu = config.vCpuCount || vCpuList[0];

  return {
    instanceFamilyList:
      customInstanceTypes.instanceFamilyList || customInstanceBuilderService.generateValidInstanceFamilyList(),
    memoryList:
      customInstanceTypes.memoryList ||
      (selectedVCpu
        ? customInstanceBuilderService.generateValidMemoryListForVCpuCount(config.instanceFamily || 'N1', selectedVCpu)
        : []),
    vCpuList,
  };
}

function isExtendedMemory(instanceType: string | null) {
  return Boolean(instanceType && instanceType.endsWith('-ext'));
}

function stripExtendedMemorySuffix(instanceType: string) {
  return isExtendedMemory(instanceType) ? instanceType.replace(/-ext$/, '') : instanceType;
}
