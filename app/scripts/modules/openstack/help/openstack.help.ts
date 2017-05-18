import {module} from 'angular';
import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from '@spinnaker/core';

const helpContents: {[key: string]: string} = {
  'openstack.loadBalancer.detail': '(Optional) A string of free-form alphanumeric characters; by convention, we recommend using "frontend".',
  'openstack.loadBalancer.stack': '(Optional) One of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'openstack.loadBalancer.subnet': 'The subnet where the instances for this load balancer reside.',
  'openstack.loadBalancer.protocol': 'The protocol for the traffic to be load balanced. Currently, only HTTP and HTTPS are supported.',
  'openstack.loadBalancer.network': 'The network containing the floating IP pool from which this load balancer will obtain and bind to a floating IP.',
  'openstack.loadBalancer.port': 'The TCP port on which this load balancer will listen.',
  'openstack.loadBalancer.targetPort': 'The TCP port on instances associated with this load balancer to which traffic is sent.',
  'openstack.loadBalancer.distribution': 'The method by which traffic is distributed to the instances.<dl><dt>Least Connections</dt><dd>Sends the request to the instance with the fewest active connections.</dd><dt>Round Robin</dt><dd>Evenly spreads requests across instances.</dd><dt>Source IP</dt><dd>Attempts to deliver requests from the same IP to the same instance.</dd></dl>',
  'openstack.loadBalancer.healthCheck.timeout': '<p>Configures the timeout, in seconds, for obtaining the healthCheck status. This value must be less than the interval.</p><p> Default: <b>1</b></p>',
  'openstack.loadBalancer.healthCheck.delay': '<p>The interval, in seconds, between health checks.</p><p>Default: <b>10</b></p>',
  'openstack.loadBalancer.healthCheck.maxRetries': '<p>The number of retries before declaring an instance as failed and removing it from the pool.</p><p>Default: <b>2</b></p>',
  'openstack.loadBalancer.healthCheck.statusCodes': 'A list of HTTP status codes that will be considered a successful response.',
  'openstack.network.floatingip': '<p>Whether or not each instance in the server group should be assigned a floating ip.</p><p>Default: <b>No</b></p>',
  'openstack.network.floatpool': 'The network from which to allocate a floating ip',
  'openstack.serverGroup.userData': '<p>Provides a script that will run when each server group instance starts.</p>',
  'openstack.serverGroup.tags': '<p>Key-value pairs of metadata that will be associate to each server group instance.</p>',
};

export const OPENSTACK_HELP = 'spinnaker.openstack.help.contents';
module(OPENSTACK_HELP, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    Object.keys(helpContents).forEach(key => helpContentsRegistry.register(key, helpContents[key]));
  });
