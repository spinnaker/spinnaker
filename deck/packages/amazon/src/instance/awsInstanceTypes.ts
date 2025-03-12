import type { IInstanceTypeCategory, IInstanceTypeFamily, IPreferredInstanceType } from '@spinnaker/core';

import { AWSProviderSettings } from '../aws.settings';

interface IAwsInstanceTypeFamily extends IInstanceTypeFamily {
  instanceTypes: IAwsPreferredInstanceType[];
}

interface IAwsPreferredInstanceType extends IPreferredInstanceType {
  cpuCreditsPerHour?: number;
}

interface IAwsInstanceTypeCategory extends IInstanceTypeCategory {
  showCpuCredits?: boolean;
  descriptionListOverride?: string[];
}

const m5: IAwsInstanceTypeFamily = {
  type: 'm5',
  description:
    'm5 instances provide a balance of compute, memory, and network resources. They are a good choice for most applications.',
  instanceTypes: [
    {
      name: 'm5.large',
      label: 'Large',
      cpu: 2,
      memory: 8,
      storage: { type: 'EBS' },
      costFactor: 2,
    },
    {
      name: 'm5.xlarge',
      label: 'XLarge',
      cpu: 4,
      memory: 16,
      storage: { type: 'EBS' },
      costFactor: 3,
    },
    {
      name: 'm5.2xlarge',
      label: '2XLarge',
      cpu: 8,
      memory: 32,
      storage: { type: 'EBS' },
      costFactor: 4,
    },
  ],
};

const t2gp: IAwsInstanceTypeFamily = {
  type: 't2',
  description:
    't2 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
  instanceTypes: [
    {
      name: 't2.small',
      label: 'Small',
      cpu: 1,
      memory: 2,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 12,
    },
    {
      name: 't2.medium',
      label: 'Medium',
      cpu: 2,
      memory: 4,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 24,
    },
    {
      name: 't2.large',
      label: 'Large',
      cpu: 2,
      memory: 8,
      storage: { type: 'EBS' },
      costFactor: 2,
      cpuCreditsPerHour: 36,
    },
    {
      name: 't2.xlarge',
      label: 'XLarge',
      cpu: 4,
      memory: 16,
      storage: { type: 'EBS' },
      costFactor: 3,
      cpuCreditsPerHour: 54,
    },
    {
      name: 't2.2xlarge',
      label: '2XLarge',
      cpu: 8,
      memory: 32,
      storage: { type: 'EBS' },
      costFactor: 4,
      cpuCreditsPerHour: 81,
    },
  ],
};

const t3gp: IAwsInstanceTypeFamily = {
  type: 't3',
  description:
    't3 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
  instanceTypes: [
    {
      name: 't3.small',
      label: 'Small',
      cpu: 2,
      memory: 2,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 24,
    },
    {
      name: 't3.medium',
      label: 'Medium',
      cpu: 2,
      memory: 4,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 24,
    },
    {
      name: 't3.large',
      label: 'Large',
      cpu: 2,
      memory: 8,
      storage: { type: 'EBS' },
      costFactor: 2,
      cpuCreditsPerHour: 36,
    },
    {
      name: 't3.xlarge',
      label: 'XLarge',
      cpu: 4,
      memory: 16,
      storage: { type: 'EBS' },
      costFactor: 3,
      cpuCreditsPerHour: 96,
    },
    {
      name: 't3.2xlarge',
      label: '2XLarge',
      cpu: 8,
      memory: 32,
      storage: { type: 'EBS' },
      costFactor: 4,
      cpuCreditsPerHour: 192,
    },
  ],
};

const t2: IAwsInstanceTypeFamily = {
  type: 't2',
  description:
    't2 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
  instanceTypes: [
    {
      name: 't2.nano',
      label: 'Nano',
      cpu: 1,
      memory: 0.5,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 3,
    },
    {
      name: 't2.micro',
      label: 'Micro',
      cpu: 1,
      memory: 1,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 6,
    },
    {
      name: 't2.small',
      label: 'Small',
      cpu: 1,
      memory: 2,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 12,
    },
  ],
};

const t3: IAwsInstanceTypeFamily = {
  type: 't3',
  description:
    't3 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
  instanceTypes: [
    {
      name: 't3.nano',
      label: 'Nano',
      cpu: 2,
      memory: 0.5,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 6,
    },
    {
      name: 't3.micro',
      label: 'Micro',
      cpu: 2,
      memory: 1,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 12,
    },
    {
      name: 't3.small',
      label: 'Small',
      cpu: 2,
      memory: 2,
      storage: { type: 'EBS' },
      costFactor: 1,
      cpuCreditsPerHour: 24,
    },
  ],
};

const r5: IAwsInstanceTypeFamily = {
  type: 'r5',
  description:
    'r5 instances are optimized for memory-intensive applications and have the lowest cost per GiB of RAM among Amazon EC2 instance types.',
  instanceTypes: [
    {
      name: 'r5.large',
      label: 'Large',
      cpu: 2,
      memory: 16,
      storage: { type: 'EBS' },
      costFactor: 1,
    },
    {
      name: 'r5.xlarge',
      label: 'XLarge',
      cpu: 4,
      memory: 32,
      storage: { type: 'EBS' },
      costFactor: 2,
    },
    {
      name: 'r5.2xlarge',
      label: '2XLarge',
      cpu: 8,
      memory: 64,
      storage: { type: 'EBS' },
      costFactor: 2,
    },
    {
      name: 'r5.4xlarge',
      label: '4XLarge',
      cpu: 16,
      memory: 128,
      storage: { type: 'EBS' },
      costFactor: 3,
    },
  ],
};

export const defaultCategories: IAwsInstanceTypeCategory[] = [
  {
    type: 'general',
    label: 'General Purpose',
    families: [m5, t2gp, t3gp],
    icon: 'hdd',
    showCpuCredits: true,
    descriptionListOverride: [
      m5.description,
      't2/t3 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
    ],
  },
  {
    type: 'memory',
    label: 'High Memory',
    families: [r5],
    icon: 'hdd',
  },
  {
    type: 'micro',
    label: 'Micro Utility',
    families: [t2, t3],
    icon: 'hdd',
    showCpuCredits: true,
    descriptionListOverride: [
      't2/t3 instances are a good choice for workloads that don’t use the full CPU often or consistently, but occasionally need to burst (e.g. web servers, developer environments and small databases).',
    ],
  },
  {
    type: 'custom',
    label: 'Custom Type',
    families: [],
    icon: 'asterisk',
  },
];

const excludeCategories = AWSProviderSettings.instanceTypes?.exclude?.categories ?? [];
const excludeFamilies = AWSProviderSettings.instanceTypes?.exclude?.families ?? [];

export const categories = defaultCategories
  .filter(({ type }) => !excludeCategories.includes(type))
  .map((category) =>
    Object.assign({}, category, {
      families: category.families.filter(({ type }) => !excludeFamilies.includes(type)),
    }),
  );
