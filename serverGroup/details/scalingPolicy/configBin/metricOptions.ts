export interface IMetricOption {
  name: string;
  description: string;
  units: string;
  idDimensions?: string[];
}

export interface IMetricOptionGroup {
  category: string;
  options: IMetricOption[];
}

export const metricOptions: IMetricOptionGroup[] = [
  {
    category: 'CPU',
    options: [
      {
        name: 'cgroup.cpu.processingCapacity',
        description: 'Amount of processing time requested for the container.',
        units: 'seconds/second'
      },
      {
        name: 'cgroup.cpu.processingTime',
        description: 'Amount of time spent processing code in the container.',
        units: 'seconds/second',
      },
      {
        name: 'cgroup.cpu.shares',
        description: 'Number of shares configured for the job. The Titus scheduler treats each CPU core as 100 shares.',
        units: 'num shares',
      },
      {
        name: 'cgroup.cpu.usageTime',
        description: 'Amount of time spent processing code in the container in either the system or user category.',
        units: 'seconds/second',
        idDimensions: ['system', 'user'],
      }
    ]
  },
  {
    category: 'Memory',
    options: [
      {
        name: 'cgroup.mem.failures',
        description: 'Counter indicating an allocation failure occurred.',
        units: 'failures/second',
      },
      {
        name: 'cgroup.mem.limit',
        description: 'Memory limit for the cgroup.',
        units: 'bytes',
      },
      {
        name: 'cgroup.mem.used',
        description: 'Memory usage for the cgroup.',
        units: 'bytes',
      },
      {
        name: 'cgroup.mem.pageFaults',
        description: `Counter indicating the number of times that a process of the cgroup triggered a "page fault" and a
     "major fault", respectively.`,
        units: 'faults/second',
        idDimensions: ['minor', 'major'],
      },
      {
        name: 'cgroup.mem.processUsage',
        description: 'Amount of memory used by processes running in the cgroup.',
        units: 'bytes',
        idDimensions: ['cache', 'rss', 'rss_huge', 'mapped_file'],
      },
    ]
  }
];
