<a name="0.17.2"></a>
### 0.17.2 (2015-01-15)

#### Bug Fixes
- Added missing nuspec files (https://github.com/markvp) 8134f82e43d04c8ae6dd95731151ad81ab7aabc0
- Remove scope from analytics-on directive. Closes #195 (https://github.com/jantimon) e8bc48eea9c0aa47cd3201d36b02ea802b5b4194
- Inline injection of $location dependency (only load $location service if needed). Closes #29 (https://github.com/elegantcoder) cb20f5caf02d00fd942315520a18491168fae73f 
- Segment - manually set the path and url becuase segment's JS lib always reports the path as '/' in `hashbang` mode 8543ef8d8cf6a933af78d9a5737c327042913a34
- Google Analytics - check for GA before _gaq (https://github.com/mkolodny) c3b33a464547a4bed39541584f93cf9542a46f5f
- Fix jquery-waypoints doesn't exist (may need to run `bower cache clean `) e68531de81526101aedca91e9721c9f0d2de322f
	- Big thanks to:
		- https://github.com/kentcdodds
		- https://github.com/imakewebthings
		- https://github.com/luisfarzati

#### Features
- Segment - identify user. Registered setUserProperties / setUserPropertiesOnce with an API to match the identify method from segment.com: https://segment.com/docs/api/tracking/identify/ (https://github.com/Normalised) 66e66f0a5a229390d1b2469ad997f13e7417c42e
	- Add optional parameters for event tracking `event, properties, options, callback`
- Add support for Google Tag Manager on Cordova resolves #258 using Tag Manager plugin. (https://github.com/kraihn) c77833d7608e865bd6b0879055c84c8b1b149735
- Add a plugin for http://tongji.baidu.com (https://github.com/miller) efbcac271ee673a11d755c2f1c387d2378b3ce98
- Google Cordova support with https://github.com/danwilson/google-analytics-plugin (https://github.com/emaV) d74387def225e49b6d6ab278d6882b14e38d79a9
- New developer mode to prevent sending data. Set `developerMode: true` in `$analyticsProvider` to stop sending data (https://github.com/tomasescobar) a0cce769f569dfb46f765eda1a97eccc2748c3f9
- Kissmetrics - enable setUsername and setUserProperties (https://github.com/jminuscula) d74387def225e49b6d6ab278d6882b14e38d79a9
- GA multi-account modification (https://github.com/robertbak) 047815f1891e7b2dfc4a4ec666b25afaca65c70d
Uses a ```$analyticsProvider.settings.ga``` object for configuration, which after initing multiple analytics accounts like this:

```javascript
    ga('create', 'UA-XXXXXX-XX');
    ga('create', 'UA-XXXXXX-XY', 'auto', {'name': 'additionalTracker1'});
    ga('create', 'UA-XXXXXX-XZ', 'auto', {'name': 'additionalTracker2'});
```

allows to configure the additional providers on startup:

```javascript
config(function ($analyticsProvider) {
    $analyticsProvider.settings.ga.additionalAccountNames = ['additionalTracker1', 'additionalTracker2'];
  });
```

You can also change the configuration while running the app which, while not the most elegant solution, allows sending only some events to multiple accounts by:

```javascript
 $analyticsProvider.settings.ga.additionalAccountNames = ['additionalTracker1'];
 $analytics.eventTrack('eventName');
 $analyticsProvider.settings.ga.additionalAccountNames = [];
```

<a name="0.17.1"></a>
### 0.17.1 (2014-10-28)

#### Bug Fixes
- Adobe event tracking - download and exit events now sent (thanks to alexismartin)
- Fix angular-scroll directive (thanks to eshaham)
- Fix for Kissmetrics to allow Karma testing

#### Features
- Add Loggly support (thanks to zoellner)
- Add Localytics support (thanks to joehalliwell)
- Add CNZZ support (thanks to L42y)
- Add Hubspot support (thanks to asafdav)
- Add main attr to package.json to enable browserify support (thanks to adamscybot)
- Updates GA provider to support custom metrics and dimensions for universal GA on a per-event basis (thanks to Philo)
- Add `mixpanel.alias` support to call event after user signs up (thanks to tomasescobar)

<a name="0.17.0"></a>
### 0.17.0 (2014-10-02)

#### Bug Fixes
- Fix double page tracking - (don't set noRoutesorState to false if conditionals fail)
- Clean up dependency injections
- Angular-scroll now minification safe

#### Features
- Add Intercom vendor plugin

<a name="0.16.5"></a>
### 0.16.5 (2014-08-28)

#### Bug Fixes
- Google Analytics - do nothing if there is no event category (required)
- Mixpanel - look for the field __loaded before sending events
- Splunk - look for _sp with a dynamic function for anyone wanting to run setup in the module.config or not pull/compile the html of their app when running a testing framework like Karma

#### Features
Evaluate initial attribute values for properties.
- This is so that events that get fired immediately have an initial value for event properties.

<a name="0.16.4"></a>
### 0.16.4 (2014-08-21)

#### Bug Fixes
Fixed #182 double pageview tracking with ui-router

#### Features
Added basic debugging provider angulartics.debug.js that uses console.log to dump page and event tracking

<a name="0.16.3"></a>
### 0.16.3 (2014-08-20)

#### Bug Fixes
- Due to changes in routing logic, Angular 1.1.5 is now the minimum supported version.
- Update all samples to Angular 1.1.5
- Fix Adobe analytics sample

<a name="0.16.2"></a>
### 0.16.2 (2014-08-19)

#### Features
* Add marketo plugin. Thanks https://github.com/cthorner
* Better releasing with grunt-bump

<a name="0.16.1"></a>
### 0.16.1 (2014-08-06)

#### Features
* **analytics-if** - Analytics call will only be made if the `analytic-if` condition passes. Example: `<button analytics-on analytics-if="user.isLoggedIn">` 
* Better buffering
* Support for multiple providers

<a name="v0.15.20"></a>
### v0.15.20 (2014-07-09)

#### Bug Fixes

* **kissmetrics:** changing kissmetrics plugin to use global window object ([d505801](https://github.com/luisfarzati/angulartics/commit/d50580181c5cbb752c2bcb1d8334c65452aac9a2), closes [#161](https://github.com/luisfarzati/angulartics/issues/161))

#### Features

* **bower** add all vendor scripts to bower to enable automatic dependency injections via wiredep and similar tools [7cbcef1](https://github.com/luisfarzati/angulartics/commit/0108263228fe24c294c5cd120122bb6570a090a2)
