'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.help.registry', [
    require('../../core/help/helpContents.registry.js'),
    require('../../core/config/settings.js'),
  ])
  .run(function(helpContentsRegistry, settings) {
    let helpContents = [
      {
        key: 'application.chaos.enabled',
        contents: '<p>Chaos Monkey periodically terminates instances in your ASGs to ensure resiliency.</p>' +
        '<p>If you do <b>not</b> want your application to participate in Chaos Monkey, unselect this option.</p>'
      },
      {
        key: 'application.legacyUdf',
        contents: '<p>The legacy user data format was used to support custom user data per account and application. We ' +
        'have since migrated away from customizing user data in favor of nflx-init.d scripts.<p>' +
        '<p>The legacy format injects <code>NETFLIX_ENVIRONMENT</code> with the name of the account, requiring ' +
        '<code>nflx-init.d</code> scripts to edit it as appropriate to correct it back to prod or test for communiction ' +
        'with shared infrastructure.</p>' +
        '<p>The new user data format injects <code>NETFLIX_ACCOUNT</code> and properly sets ' +
        '<code>NETFLIX_ENVIRONMENT</code>. If you have existing <code>nflx-init.d</code> scripts that are expecting to ' +
        'make that modification, and performing other actions based on the initial value of ' +
        '<code>NETFLIX_ENVIRONMENT</code> those scripts will need to be updated before opting in to use the new user ' +
        'data format.</p>'
      },
      {
        key: 'chaos.meanTime',
        contents: '<p>The average number of days between kills for each group</p>'
      },
      {
        key: 'chaos.minTime',
        contents: '<p>The minimum number of days Chaos Monkey will leave the groups alone</p>'
      },
      {
        key: 'chaos.grouping',
        contents: '<p>Tells Chaos Monkey how to decide which instances to terminate:</p>' +
        '<ul>' +
          '<li><b>App:</b> Only kill one instance in the entire application, across stacks and clusters</li>' +
          '<li><b>Stack:</b> Only kill one instance in each stack</li>' +
          '<li><b>Cluster:</b> Kill an instance in every cluster</li>' +
        '</ul>'
      },
      {
        key: 'chaos.regions',
        contents: '<p>If selected, Chaos Monkey will treat each region in each group separately, e.g. if your cluster ' +
        'is deployed in three regions, an instance in each region would be terminated.</p>'
      },
      {
        key: 'chaos.exceptions',
        contents: '<p>When Chaos Monkey is enabled, exceptions tell Chaos Monkey to leave certain clusters alone. ' +
        'You can use wildcards (*) to include all matching fields.</p>'
      },
      {
        key: 'pipeline.config.bake.package',
        contents: '<p>The name of the package you want installed (without any version identifiers).</p>' +
        '<p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>'
      }
    ];
    if (settings.feature && settings.feature.netflixMode) {
      helpContents.forEach((entry) => helpContentsRegistry.register(entry.key, entry.contents));
    }
  });
