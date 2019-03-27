export interface IMetricOption {
  metricName: string;
  metric: string;
  description: string;
  units: string;
}

export const titusMetricOptions: IMetricOption[] = [
  {
    metric: 'name,cgroup.cpu.processingTime,:eq,100,:mul,name,cgroup.cpu.processingCapacity,:eq,:div',
    metricName: 'cgroup.cpu.utilization',
    description: 'Average CPU Utilization',
    units: 'percent',
  },
  {
    metric: 'name,ipc.server.call,:eq,statistic,count,:eq,:and,:avg',
    metricName: 'ipc.throughputAverage',
    description: 'Average Throughput (RPS) per node',
    units: 'requests',
  },
];
