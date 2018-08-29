import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents = [
  {
    key: 'oracle.serverGroup.stack',
    value: '(Optional) <b>Stack</b> Stack name',
  },
  {
    key: 'oracle.serverGroup.detail',
    value: '(Optional) <b>Detail</b> is a naming component to help distinguish specifics of the server group.',
  },
  {
    key: 'oracle.pipeline.config.bake.baseOsOption',
    value: '<p>The base operating system from which the image will be created.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.package',
    value:
      '<p>The name of the package you want installed (without any version identifiers).</p>' +
      '<p>For example: <i>curl</i>.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.upgrade',
    value:
      '<p>Perform a package manager upgrade before proceeding with the package installation.</p>' +
      '<p>For example: <i>yum update</i>.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.regions',
    value:
      '<p>The region in which the new image will be created.</p>' +
      '<p>NB: <i>Currently baked images are restricted to a single region</i>.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.user',
    value: '<p>The name of Oracle <i>user</i> that will be used during the baking process.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.account_name',
    value: '<p>The name of Oracle <i>account</i> that will be used during the baking process.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.network',
    value: '<p>The name of Oracle <i>network</i> that will be used during the baking process.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.availability_domain',
    value: '<p>The Oracle <i>availability domain</i> that will be used during the baking process.</p>',
  },
  {
    key: 'oracle.pipeline.config.bake.subnet_ocid',
    value: '<p>The Oracle <i>subnet</i> that will be used during the baking process.</p>',
  },
];

helpContents.forEach(entry => HelpContentsRegistry.register(entry.key, entry.value));
