import { module } from 'angular';

import { HELP_CONTENTS_REGISTRY, HelpContentsRegistry } from '@spinnaker/core';

import { NetflixSettings } from '../netflix.settings';

export const NETFLIX_HELP_REGISTRY = 'spinnaker.netflix.help.registry';
module(NETFLIX_HELP_REGISTRY, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    const helpContents: {[key: string]: string} = {
      'application.legacyUdf': `
          <p>The legacy user data format was used to support custom user data per account and application. We
          have since migrated away from customizing user data in favor of nflx-init.d scripts.<p>
          <p>The legacy format injects <code>NETFLIX_ENVIRONMENT</code> with the name of the account, requiring
          <code>nflx-init.d</code> scripts to edit it as appropriate to correct it back to prod or test for communication
          with shared infrastructure.</p>
          <p>The new user data format injects <code>NETFLIX_ACCOUNT</code> and properly sets
          <code>NETFLIX_ENVIRONMENT</code>. If you have existing <code>nflx-init.d</code> scripts that are expecting to
          make that modification, and performing other actions based on the initial value of
          <code>NETFLIX_ENVIRONMENT</code> those scripts will need to be updated before opting in to use the new user
          data format.</p>`,
      'pipeline.config.bake.package': `
          <p>The name of the package you want installed (without any version identifiers).</p>
          <p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>`,
      'pipeline.config.isolatedTestingTarget.clusters': `
          <p>These clusters will allow you to select a cluster that you want to mimic, and will clone the properties
          and override the VIP to provide an isolated testing target.</p>`,
      'pipeline.config.isolatedTestingTarget.vips': `
          <p>These VIPs will show the value of the VIP of the old cluster that is being copied from and the VIP that will be given to the new cluster.</p>`,
      'chaos.documentation': `
          <p>Chaos Monkey documentation can be found
             <a target="_blank" href="https://stash.corp.netflix.com/pages/TRAFFIC/chaosmonkey/pages/browse/">here</a>.
          </p>`,
      'fastProperty.constraints': `
          <ul>
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
          <a href="https://confluence.netflix.com/display/PDCLOUD/Validation+for+Persisted+Properties" target="_blank">Full Documentation</a>`,
      'availability.context': 'The availability trends for the Netflix streaming service represented as percentage of expected stream starts acheived and the number of \'nines\' of that availability. <a href="http://go/availabilitycontextdoc" target="_blank">Click here</a> for more details.',
      'pipeline.config.canary.clusterPairs': `
        <p>A <em>cluster pair</em> is used to create a baseline and canary cluster.</p>' +
        <p>The version currently deployed in the baseline cluster will be used to create a new baseline server group, while the version created in the previous bake or Find AMI stage will be deployed into the canary.</p>`,

      'pipeline.config.canary.resultStrategy': `
        <p>The result stategy is used to determine how to roll up a score if multiple clusters are participating in the canary.</p>
        <p>The <em>lowest</em> strategy means that the cluster with the lowest score is used as the rolled up score</p>
        <p>The <em>average</em> strategy takes the average of all the canary scores</p>`,

      'pipeline.config.canary.delayBeforeAnalysis': '<p>The number of minutes until the first ACA measurement interval begins.</p>',

      'pipeline.config.canary.notificationHours': '<p>Hours at which to send a notification (comma separated)</p>',

      'pipeline.config.canary.canaryInterval': '<p>The frequency at which a canary score is generated.  The recommended interval is at least 30 minutes.</p>',

      'pipeline.config.canary.successfulScore': '<p>The minimum score the canary must achieve to be considered successful.</p>',
      'pipeline.config.canary.unhealthyScore': '<p>The lowest score the canary can attain before it is aborted and disabled as a failure.</p>',
      'pipeline.config.canary.scaleUpCapacity': '<p>The number of instances to which to scale the canary and control clusters.</p>',
      'pipeline.config.canary.scaleUpDelay': '<p>The number of minutes to wait before scaling up the canary.</p>',
      'pipeline.config.canary.baselineVersion': '<p>The Canary stage will inspect the specified cluster to determine which version to deploy as the baseline in each cluster pair.</p>',
      'pipeline.config.canary.lookback': '<p>With an analysis type of <strong>Growing</strong>, ACA will look at the entire duration of the canary for its analysis.</p><p>When choosing <strong>Sliding Lookback</strong>, the canary will use the most recent number of specified minutes for its analysis report (<b>useful for long running canaries that span multiple days</b>).</p>',
      'pipeline.config.canary.continueOnUnhealthy': '<p>Continue the pipeline if the ACA comes back as <b>UNHEALTHY</b></p>',
      'pipeline.config.canary.owner': '<p>The recipient email to which the canary report(s) will be sent.</p>',
      'pipeline.config.canary.watchers': '<p>Comma separated list of additional emails to receive canary reports.  Owners are automatically subscribed to notification emails.</p>',
      'pipeline.config.canary.useGlobalDataset': '<p>Uses the global atlas dataset instead of the region specific dataset for ACA</p>',
      'fastProperty.canary.strategy.rolloutList': '<p>A comma separated list of numbers or percentages of instance canary against.</p>',
      'pipeline.config.fastProperty.rollback': 'Enables the Fast Property to be rolled back to it previous state when the pipeline completes.',
      'pipeline.config.quickPatchAsg.cluster': '<p>Configures the cluster upon which this quick patch operation will act.</p>',
      'pipeline.config.quickPatchAsg.package': '<p>The name of the package you want installed (without any version identifiers).</p>',
      'pipeline.config.quickPatchAsg.baseOs': '<p>The operating system running on the target instances.</p>',
      'pipeline.config.quickPatchAsg.rollingPatch': '<p>Patch one instance at a time vs. all at once.</p>',
      'pipeline.config.quickPatchAsg.skipUpToDate': '<p>Skip instances which already have the requested version.</p>',

    };

    if (NetflixSettings.feature.netflixMode) {
      Object.keys(helpContents).forEach(key => helpContentsRegistry.register(key, helpContents[key]));
    }
  });
