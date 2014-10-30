'use strict';

angular.module('deckApp')
  .constant('helpContents', {
    'aws.serverGroup.subnet': 'This is the VPC in which your server group will run. Options vary by account and region; the most common ones are:' +
      '<ul>' +
      '<li><b>None (EC2 Classic)</b>: instances will not run in a VPC</li>' +
      '<li><b>internal</b> instances will be restricted to internal clients (i.e. require VPN access)</li>' +
      '<li><b>external</b> instances will be publicly accessible and running in VPC</li>' +
      '</ul>',
    'aws.loadBalancer.subnet': 'This is the VPC in which your load balancer will run.<br/>' +
      ' This also restricts the server groups which can be serviced by the load balancer.' +
      ' Options vary by account and region; the most common ones are:' +
      '<ul>' +
      '<li><b>None (EC2 Classic)</b>: the load balancer will not run in a VPC</li>' +
      '<li><b>internal</b> access to the load balancer will be restricted to internal clients (i.e. require VPN access)</li>' +
      '<li><b>external</b> the load balancer will be publicly accessible and running in VPC</li>' +
      '</ul>',
    'aws.serverGroup.stack': '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
    'aws.serverGroup.detail': '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables',
    'aws.serverGroup.imageName': '(Required) <b>Image</b> is the deployable Amazon Machine Image. Images are restricted to the account and region selected.',
    'aws.serverGroup.allImages': 'Search for an image that does not match the name of your application.',
    'aws.serverGroup.filterImages': 'Select from a pre-filtered list of images matching the name of your application.'
  });
