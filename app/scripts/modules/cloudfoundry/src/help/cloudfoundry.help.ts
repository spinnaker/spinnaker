import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'cf.service.deploy.timeout':
    '(Optional) <b>Override Deploy Timeout</b> is the maximum amount of time allowed for service deployment before Spinnaker reports a timeout error (default: 450 seconds). If a timeout error is reported by Spinnaker, the request may still be processed and a service may still be created.',
  'cf.service.destroy.timeout':
    '(Optional) <b>Override Destroy Timeout</b> is the maximum amount of time allowed for service destruction before Spinnaker reports a timeout error (default: 450 seconds). If a timeout error is reported by Spinnaker, the request may still be processed and the service may be deleted.',
  'cf.serverGroup.stack':
    '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'cf.serverGroup.detail':
    '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.',
  'cf.serverGroup.startApplication':
    '<b>Start on creation</b> is a boolean value that determines if the server group is started upon creation. Default value is <code>true</code>.',
  'cf.serverGroup.requiredRoutes':
    '<b>Route</b> is a URI in the form of <code>some.host.some.domain[:9999][/some/path]</code> (port and path are optional). The domain has to be a valid domain in the CloudFoundry Org (Region) that this server group runs in.',
  'cf.serverGroup.routes':
    '(Optional) <b>Route</b> is a URI in the form of <code>some.host.some.domain[:9999][/some/path]</code> (port and path are optional). The domain has to be a valid domain in the CloudFoundry Org (Region) that this server group runs in.',
  'cf.artifact.package': `<p>This option allows you to create a new server group from an existing Droplet. You can use this to relocate an existing Cloud Foundry application from one space to another or to launch a clone of an application with different root filesystem or resource settings.</p>`,
  'cf.artifact.trigger.account': `<p>Specify an artifact account if the trigger source produces an artifact that requires authentication credentials to retrieve. Otherwise, leave blank.</p>`,
  'cf.runJob.logsUrl':
    '<p>(Optional) A templated URL to an external logging system. The URL is used in the pipeline execution stage task details.</p>' +
    '<p><a href="http://spinnaker.github.io/guides/user/pipeline-expressions" target="_blank">Pipeline expressions</a> can be used to interpolate values from the stage context, for example:' +
    '<ul>' +
    '<li><code>${appGuid}</code> the selected cluster CF app GUID.</li>' +
    '<li><code>${name}</code> the task name.</li>' +
    '</ul>' +
    '</p>',
  'cf.runJob.jobName': '(Optional) If left empty, a random string will be generated.',
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
