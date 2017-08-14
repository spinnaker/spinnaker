const METRIC_PREFIX = 'compute.googleapis.com/';

// https://cloud.google.com/monitoring/api/metrics#gcp-compute
// This is definitely just a short-term placeholder - the set of metrics will probably be loaded from Kayenta.
const METRIC_TYPES = [
  'firewall/dropped_bytes_count',
  'firewall/dropped_packets_count',
  'instance/cpu/reserved_cores',
  'instance/cpu/usage_time',
  'instance/cpu/utilization',
  'instance/disk/read_bytes_count',
  'instance/disk/read_ops_count',
  'instance/disk/throttled_read_bytes_count',
  'instance/disk/throttled_read_ops_count',
  'instance/disk/throttled_write_bytes_count',
  'instance/disk/throttled_write_ops_count',
  'instance/disk/write_bytes_count',
  'instance/disk/write_ops_count',
  'instance/network/received_bytes_count',
  'instance/network/received_packets_count',
  'instance/network/sent_bytes_count',
  'instance/network/sent_packets_count',
  'instance/uptime',
];

export function getMetricTypes(): string[] {
  return METRIC_TYPES.map(type => `${METRIC_PREFIX}${type}`);
}
