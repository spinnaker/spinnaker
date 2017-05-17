import { module } from 'angular';

import { HELP_CONTENTS_REGISTRY, HelpContentsRegistry } from '@spinnaker/core';

import { NetflixSettings } from '../netflix.settings';

interface IHelpItem {
  key: string;
  contents: string;
}

export const NETFLIX_HELP_REGISTRY = 'spinnaker.netflix.help.registry';
module(NETFLIX_HELP_REGISTRY, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    const helpContents: IHelpItem[] = [
      {
        key: 'application.legacyUdf',
        contents: `<p>The legacy user data format was used to support custom user data per account and application. We
        have since migrated away from customizing user data in favor of nflx-init.d scripts.<p>
        <p>The legacy format injects <code>NETFLIX_ENVIRONMENT</code> with the name of the account, requiring
        <code>nflx-init.d</code> scripts to edit it as appropriate to correct it back to prod or test for communication
        with shared infrastructure.</p>
        <p>The new user data format injects <code>NETFLIX_ACCOUNT</code> and properly sets
        <code>NETFLIX_ENVIRONMENT</code>. If you have existing <code>nflx-init.d</code> scripts that are expecting to
        make that modification, and performing other actions based on the initial value of
        <code>NETFLIX_ENVIRONMENT</code> those scripts will need to be updated before opting in to use the new user
        data format.</p>`
      },
      {
        key: 'pipeline.config.bake.package',
        contents: `<p>The name of the package you want installed (without any version identifiers).</p>
        <p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>`
      },
      {
        key: 'pipeline.config.isolatedTestingTarget.clusters',
        contents: `<p>These clusters will allow you to select a cluster that you want to mimic, and will clone the properties
        and override the VIP to provide an isolated testing target.</p>`
      },
      {
        key: 'pipeline.config.isolatedTestingTarget.vips',
        contents: `<p>These VIPs will show the value of the VIP of the old cluster that is being copied from and the VIP that will be given to the new cluster.</p>`
      },
      {
        key: 'chaos.documentation',
        contents: `<p>Chaos Monkey documentation can be found
                   <a target="_blank" href="https://stash.corp.netflix.com/pages/TRAFFIC/chaosmonkey/pages/browse/">
                     here
                   </a>.
                 </p>`
      },
      {
        key: 'titus.deploy.securityGroups',
        contents: 'AWS Security Groups to assign to this service. Security groups are set only if <samp>Allocate IP?</samp> has been selected and are assigned to the Titus AWS Elastic Network Interface.'
      },
      {
        key: 'titus.job.securityGroups',
        contents: 'AWS Security Groups to assign to this job'
      },
      {
        key: 'fastProperty.constraints',
        contents: `<ul>
                    <li>int</li>
                    <li>int: 100-200</li>
                    <li>boolean</li>
                    <li>range: 100-</li>
                    <li>range: 100-200</li>
                    <li>min: 0.1</li>
                    <li>max: 100</li>
                    <li>length: 2-10</li>
                    <li>json</li>
                    <li>url</li>
                    <li>url: {protocol: &quot;http/&quot;}</li>
                    <li>pattern: &quot;^\\w*$/&quot;</li>
                    <li>pattern: &quot;INFO|WARN|ERROR&quot;</li>
                    <li>email</li>
                    <li>none</li>
                  </ul>
                  <a href="https://confluence.netflix.com/display/PDCLOUD/Validation+for+Persisted+Properties" target="_blank">Full Documentation</a>`
      },
      {
        key: 'availability.context',
        contents: 'The availability trends for the Netflix streaming service represented as percentage of expected stream starts acheived and the number of \'nines\' of that availability. <a href="http://go/availabilitycontextdoc" target="_blank">Click here</a> for more details.'
      }
    ];

    if (NetflixSettings.feature.netflixMode) {
      helpContents.forEach((entry: IHelpItem) => helpContentsRegistry.registerOverride(entry.key, entry.contents));
    }
  });
