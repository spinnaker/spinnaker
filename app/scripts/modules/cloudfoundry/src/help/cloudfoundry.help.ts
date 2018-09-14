import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'cf.serverGroup.stack':
    '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'cf.serverGroup.detail':
    '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.',
  'cf.serverGroup.startApplication':
    '<b>Start on creation</b> is a boolean value that determines if the server group is started upon creation. Default value is <code>true</code>',
  'cf.serverGroup.routes':
    '(Optional) <b>Route</b> is a URI in the form of <code>some.host.some.domain[:9999][/some/path]</code> (port and path are optional). The domain has to be a valid domain in the CloudFoundry Org (Region) that this server group runs in.',
  'cf.artifact.package': `<p>This option allows you to create a new server group from an existing Droplet. You can use this to relocate an existing Cloud Foundry application from one space to another or to launch a clone of an application with different root filesystem or resource settings.</p>`,
  'cf.artifact.trigger.account': `<p>Specify an artifact account if the trigger source produces an artifact that requires authentication credentials to retrieve. Otherwise, leave blank.</p>`,
};

Object.keys(helpContents).forEach(key => HelpContentsRegistry.register(key, helpContents[key]));
