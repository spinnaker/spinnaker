import { module } from 'angular';

import { HELP_CONTENTS_REGISTRY, HelpContentsRegistry } from '@spinnaker/core';

export const ORACLE_HELP_CONTENTS_REGISTRY = 'spinnaker.oracle.helpContents.registry';
module(ORACLE_HELP_CONTENTS_REGISTRY, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    const helpContents = [
      {
        key: 'oraclebmcs.serverGroup.stack',
        value: '(Optional) <b>Stack</b> Stack name'
      },
      {
        key: 'oraclebmcs.serverGroup.detail',
        value: '(Optional) <b>Detail</b> is a naming component to help distinguish specifics of the server group.'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.baseOsOption',
        value: '<p>The base operating system from which the image will be created.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.package',
        value: '<p>The name of the package you want installed (without any version identifiers).</p>' +
               '<p>For example: <i>curl</i>.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.upgrade',
        value: '<p>Perform a package manager upgrade before proceeding with the package installation.</p>' +
               '<p>For example: <i>yum update</i>.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.regions',
        value: '<p>The region in which the new image will be created.</p>' +
               '<p>NB: <i>Currently baked images are restricted to a single region</i>.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.user',
        value: '<p>The name of Oracle BMCS <i>user</i> that will be used during the baking process.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.account_name',
        value: '<p>The name of Oracle BMCS <i>account</i> that will be used during the baking process.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.network',
        value: '<p>The name of Oracle BMCS <i>network</i> that will be used during the baking process.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.availability_domain',
        value: '<p>The Oracle BMCS <i>availability domain</i> that will be used during the baking process.</p>'
      },
      {
        key: 'oraclebmcs.pipeline.config.bake.subnet_ocid',
        value: '<p>The the Oracle BMCS <i>subnet</i> that will be used during the baking process.</p>'
      },
    ];

    helpContents.forEach((entry) => helpContentsRegistry.register(entry.key, entry.value));
  });
