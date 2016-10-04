'use strict';

import registryModule from '../../core/help/helpContents.registry';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.help.registry', [
    registryModule,
    require('../../core/config/settings.js'),
  ])
  .run(function(helpContentsRegistry, settings) {
    let helpContents = [
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
        key: 'chaos.documentation',
        contents: `<p>Chaos Monkey documentation can be found
                   <a target="_blank" href="https://stash.corp.netflix.com/pages/TRAFFIC/chaosmonkey/pages/browse/">
                     here
                   </a>.
                 </p>`
      }
    ];
    if (settings.feature && settings.feature.netflixMode) {
      helpContents.forEach((entry) => helpContentsRegistry.registerOverride(entry.key, entry.contents));
    }
  });
