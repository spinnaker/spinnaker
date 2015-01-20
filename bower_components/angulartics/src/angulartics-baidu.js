/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * Contributed by http://github.com/miller
 * License: MIT
 */

(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.baidu
 * Enables analytics support for baidu (http://tongji.baidu.com)
 */
angular.module('angulartics.baidu', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {

  if (window._hmt) {
	_hmt.push(['_setAutoPageview', false]);
  }

  $analyticsProvider.registerPageTrack(function (path) {
    if (window._hmt) {
      _hmt.push(['_trackPageview', path]);
    }
  });

  $analyticsProvider.registerEventTrack(function (action, properties) {
    // do nothing if there is no category or action (it's required by baidu)
    if (!window._hmt || !properties || !properties.category || !properties.action) { 
		return; 
	}

	var eventData = [ '_trackEvent', properties.category, properties.action ];

	if (properties.label) {
		eventData.push(properties.label);
	}

	if (properties.value) {
		eventData[4] = properties.value;
	}

	_hmt.push(eventData);
  });

}]);
})(angular);
