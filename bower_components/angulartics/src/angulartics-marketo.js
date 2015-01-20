/**
 * @license Angulartics v0.17.2
 * (c) 2014 Carl Thorner http://luisfarzati.github.io/angulartics
 * Contributed by http://github.com/L42y
 * License: MIT
 */
(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.marketo
 * Enables analytics support for Marketo (http://www.marketo.com)
 *
 * Will not be considered loaded until the sKey attribute is set on the Munckin object, like so:
 *
 * Munckin.skey = 'my-secret-key';
 *
 * for event tracking email is a required attribute
 */
angular.module('angulartics.marketo', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {
  angulartics.waitForVendorApi('Munchkin', 500, 'sKey', function (Munchkin) {
    $analyticsProvider.registerPageTrack(function (path) {
      Munchkin.munchkinFunction("visitWebPage", {url: path} );
    });
  });

  // If a path is set as a property we do a page tracking event.
  angulartics.waitForVendorApi('Munchkin', 500, 'sKey', function (Munchkin) {
   $analyticsProvider.registerEventTrack(function (action, properties) {
    if(properties.path !== undefined) {
     var params = [];
     for(var prop in properties){
      if(prop !== 'path') {
       params.push(prop + "=" + properties[prop]);
      }
     }
     if(action.toUpperCase() == 'CLICK'){
      Munchkin.munchkinFunction('clickLink', {
       href: properties.path
      });
     }
     Munchkin.munchkinFunction("visitWebPage", {url: properties.path, params: params.join("&")});
    }
   });
  });

  var associateLead = function(properties){
    if(properties.email !== undefined) {
      email = properties.email;
      email_sha = sha1(Munckin.sKey + email);
      properties.Email = properties.email;
      Munchkin.munchkinFunction('associateLead', properties, email_sha);
    }
  };

  angulartics.waitForVendorApi('Munchkin', 500, function (Munchkin) {
    $analyticsProvider.registerSetUsername(function (userId) {
      if(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}/.test(userId)){
       associateLead({'Email': userId});
      }
    });
  });

  angulartics.waitForVendorApi('Munchkin', 500, function (Munchkin) {
    $analyticsProvider.registerSetUserProperties(function (properties) {
     associateLead(properties);
    });
  });

  angulartics.waitForVendorApi('Munchkin', 500, function (Munchkin) {
    $analyticsProvider.registerSetUserPropertiesOnce(function (properties) {
     associateLead(properties);
    });
  });
}]);
})(angular);
