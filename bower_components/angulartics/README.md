angulartics
===========
[![Gitter](https://badges.gitter.im/Join Chat.svg)](https://gitter.im/luisfarzati/angulartics?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Vendor-agnostic analytics for AngularJS applications. [luisfarzati.github.io/angulartics](http://luisfarzati.github.io/angulartics "Go to the website")

# Install

## Manual

Download complete package from http://github.com/luisfarzati/angulartics/archive/master.zip

## NuGet

Manage the NuGet Packages for your project and search for Angular.Analytics. Select the module for the analytics package you wish to use and the Angular.Analytics.Core package will be included. Also install any additional modules you require such as Angular.Analytics.Scroll.

Alternatively, at the Package Manager Console type:
	Install-Package Angular.Analytics.[ModuleName]

Or, in a command line (with nuget.exe in your path):
	nuget.exe install Angular.Analytics.[ModuleName]

## Bower

To install all available modules:
	bower install angulartics

# Full path tracking (for pages without a router)
Introduced in 0.15.19 - support websites that do not use Angular `routes` or `states` on every page and still want to track full paths.  The modifications lead to the following behavior:

 - **Viewing page `http://host.com/routes#/route` will be tracked as `/routes#/route`.** The original version would only track the page as `/route`
 - **Viewing page `http://host.com/noroutes` will be tracked as `/noroutes`.**  This is useful for pages that do not contain Angular code besides initializing the base module.
 - **Viewing page `http://host.com/routes2` that loads a default route and changes the path to `http://host.com/routes2#/` will be tracked as `/routes2#/`.** This will only fire one pageview, whereas earlier versions would have fired two.

To enable this behavior, add the following to your configuration:

		...
		var yourApp = angular.module('YourApp', ['angulartics', 'angulartics.google.analytics'])
		    .config(function ($analyticsProvider) {
		        $analyticsProvider.firstPageview(true); /* Records pages that don't use $state or $route */
		        $analyticsProvider.withAutoBase(true);  /* Records full path */
		});

# Minimal setup

## for Google Analytics ##

    angular.module('myApp', ['angulartics', 'angulartics.google.analytics'])

Delete the automatic pageview tracking line in the snippet code provided by Google Analytics (because Angulartics will automatically track pages for you):

      ...
      ga('create', '{YOUR GA CODE}', '{YOUR DOMAIN}');
      ga('send', 'pageview'); // <-- DELETE THIS LINE!
    </script>
    
Done. Open your app, browse across the different routes and check [the realtime GA dashboard](http://google.com/analytics/web) to see the hits. 

## for Google Tag Manager

    angular.module('myApp', ['angulartics', 'angulartics.google.tagmanager'])
    
Add the full tracking code from Google Tag Manager to the beginning of your body tag.

Setup listeners in Google Tag Manager

* 6 Macros
    - Macro Name: angulartics page path
        - Macro Type: Data Layer Variable
        - Data Layer Variable Name: content-name
    - Macro Name: angulartics event category
        - Macro Type: Data Layer Variable
        - Data Layer Variable Name: target
    - Macro Name: angulartics event action
        - Macro Type: Data Layer Variable
        - Data Layer Variable Name: action
    - Macro Name: angulartics event label
        - Macro Type: Data Layer Variable
        - Data Layer Variable Name: target-properties
    - Macro Name: angulartics event value
        - Macro Type: Data Layer Variable
        - Data Layer Variable Name: value
    - Macro Name: angulartics event interaction type
        - Macro Type: Data Layer Variable
        - Data Layer Variable Name: interaction-type
* 2 Rules
    - Rule Name: Angulartics events
        - Condition: {{event}} equals interaction
    - Rule Name: Angulartics pageviews
        - Condition: {{event}} equals content-view
* 2 Tags
    - Tag Name: Angulartics Events
        - Tag Type: Universal Analytics
        - Tracking ID: YourGoogleAnalyticsID
        - Track Type: Event
        - Category: {{angulartics event category}}
        - Action: {{angulartics event action}}
        - Label: {{angulartics event label}}
        - Value: {{angulartics event value}}
        - Non-Interaction Hit: {{angulartics event interaction type}}
        - Firing Rules: Angulartics events
    - Tag Name: Angulartics Pageviews
        - Tag Type: Universal Analytics
        - Tracking ID: YourGoogleAnalyticsID
        - Track Type: Page View
        - More settings
            - Basic Confiruration
                - Document Path: {{angulartics page path}}
        - Firing Rules: Angulartics pageviews

## for other providers

[Browse the website for detailed instructions.](http://luisfarzati.github.io/angulartics)

## Supported providers

* Adobe Analytics
* Chartbeat
* Flurry
* Google Analytics
* Google Tag Manager
* HubSpot
* Kissmetrics
* Localytics
* Loggly
* Marketo
* Mixpanel
* Piwik
* Scroll tracking
* Segment.io
* Splunk
* Woopra

If there's no Angulartics plugin for your analytics vendor of choice, please feel free to write yours and PR' it! Here's how to do it.

## Creating your own vendor plugin ##

It's very easy to write your own plugin. First, create your module and inject `$analyticsProvider`:

	angular.module('angulartics.myplugin', ['angulartics'])
	  .config(['$analyticsProvider', function ($analyticsProvider) {

Please follow the style `angulartics.{vendorname}`.

Next, you register either the page track function, event track function, or both. You do it by calling the `registerPageTrack` and `registerEventTrack` methods. Let's take a look at page tracking first:

    $analyticsProvider.registerPageTrack(function (path) {
		// your implementation here
	}

By calling `registerPageTrack`, you tell Angulartics to invoke your function on `$routeChangeSuccess` or `$stateChangeSuccess`. Angulartics will send the new path as an argument.

    $analyticsProvider.registerEventTrack(function (action, properties) {
		// your implementation here

This is very similar to page tracking. Angulartics will invoke your function every time the event (`analytics-on` attribute) is fired, passing the action (`analytics-event` attribute) and an object composed of any `analytics-*` attributes you put in the element.

Check out the bundled plugins as reference. If you still have any questions, feel free to email me or post an issue at GitHub!

# Playing around

## Disabling virtual pageview tracking

If you want to keep pageview tracking for its traditional meaning (whole page visits only), set virtualPageviews to false:

	module.config(function ($analyticsProvider) {
		$analyticsProvider.virtualPageviews(false);     

## Programmatic tracking

Use the `$analytics` service to emit pageview and event tracking:

	module.controller('SampleCtrl', function($analytics) {
		// emit pageview beacon with path /my/url
	    $analytics.pageTrack('/my/url');

		// emit event track (without properties)
	    $analytics.eventTrack('eventName');

		// emit event track (with category and label properties for GA)
	    $analytics.eventTrack('eventName', { 
	      category: 'category', label: 'label'
        }); 

## Declarative tracking

Use `analytics-on` and `analytics-event` attributes for enabling event tracking on a specific HTML element:

	<a href="file.pdf" 
		analytics-on="click"
        analytics-if="myScope.shouldTrack"
		analytics-event="Download">Download</a>

`analytics-on` lets you specify the DOM event that triggers the event tracking; `analytics-event` is the event name to be sent.

`analytics-if` is a conditional check. If the attribute value evaluates to a falsey, the event will NOT be fired. Useful for user tracking opt-out, etc.

Additional properties (for example, category as required by GA) may be specified by adding `analytics-*` attributes:

	<a href="file.pdf" 
		analytics-on="click" 
		analytics-event="Download"
		analytics-category="Content Actions">Download</a>

# What else?

See full docs and more samples at [http://luisfarzati.github.io/angulartics](http://luisfarzati.github.io/angulartics "http://luisfarzati.github.io/angulartics").

# License

Angulartics is freely distributable under the terms of the MIT license.

Copyright (c) 2013 Luis Farzati

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/luisfarzati/angulartics/trend.png)](https://bitdeli.com/free "Bitdeli Badge")

