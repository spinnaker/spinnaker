# pylint: disable=missing-docstring

import spectator_client

class DummyMetricsService(object):
  def publish_metrics(self, service_metrics):
    spectator_client.foreach_metric_in_service_map(service_metrics, self.dump)
    return -1

  def dump(self, service, name, instance, metric_data, service_data):
    # pylint: disable=unused-argument
    name, tags = spectator_client.normalize_name_and_tags(
        name, instance, metric_data)
    if tags is None:
      print 'IGNORE {0}.{1}'.format(service, name)
    else:
      print '{0}.{1} {2} {3}'.format(
          service, name,
          [(tag['key'], tag['value']) for tag in tags],
          instance['values'])
