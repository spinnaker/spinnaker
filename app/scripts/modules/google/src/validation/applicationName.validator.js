'use strict';

import { module } from 'angular';

import { ApplicationNameValidator, FirewallLabels } from '@spinnaker/core';

export const GOOGLE_VALIDATION_APPLICATIONNAME_VALIDATOR = 'spinnaker.gce.validation.applicationName';
export const name = GOOGLE_VALIDATION_APPLICATIONNAME_VALIDATOR; // for backwards compatibility
module(GOOGLE_VALIDATION_APPLICATIONNAME_VALIDATOR, [])
  .factory('gceApplicationNameValidator', function () {
    function validateSpecialCharacters(name, errors) {
      const pattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
      if (!pattern.test(name)) {
        errors.push(
          'The application name must begin with a letter and must contain only letters or digits. No ' +
            'special characters are allowed.',
        );
      }
    }

    function validateLength(name, warnings, errors) {
      // GCE resource names must comply with https://www.ietf.org/rfc/rfc1035.txt
      // [a-z]([-a-z0-9]*[a-z0-9])?
      const maxResourceNameLength = 63;

      // e.g. $appName-$stack-$detail-tp-1451531076528 and $appName-$stack-$detail-hc-1451531076528
      const loadBalancerNameSuffixLength = 17;
      const maxLengthForLoadBalancers = maxResourceNameLength - loadBalancerNameSuffixLength;

      // e.g. $appName-$stack-$detail-v000-abcd
      const instanceNameSuffixLength = 10;
      const maxLengthForServerGroups = maxResourceNameLength - instanceNameSuffixLength;

      if (name.length > maxResourceNameLength) {
        errors.push('The maximum length for an application in Google is 63 characters.');
        return;
      }

      if (name.length > maxLengthForLoadBalancers - 12) {
        if (name.length > maxLengthForLoadBalancers) {
          warnings.push(`You will not be able to create a Google load balancer for this application if the
            application's name is longer than ${maxLengthForLoadBalancers} characters (currently: ${name.length}
          characters).`);
        } else if (name.length >= maxLengthForLoadBalancers - 2) {
          warnings.push(
            'With separators ("-"), you will not be able to include a stack and detail field for ' +
              'Google load balancers.',
          );
        } else {
          const remaining = maxLengthForLoadBalancers - 2 - name.length;
          warnings.push(`If you plan to include a stack or detail field for Google load balancers, you will only
            have ~${remaining} characters to do so.`);
        }
      }

      if (name.length > maxLengthForServerGroups - 12) {
        if (name.length > maxLengthForServerGroups) {
          warnings.push(`You will not be able to create a Google server group for this application if the
            application's name is longer than ${maxLengthForServerGroups} characters (currently: ${name.length}
            characters).`);
        } else if (name.length >= maxLengthForServerGroups - 2) {
          warnings.push(
            'With separators ("-"), you will not be able to include a stack and detail field for ' +
              'Google server groups.',
          );
        } else {
          const remaining = maxLengthForServerGroups - 2 - name.length;
          warnings.push(`If you plan to include a stack or detail field for Google server groups, you will only
            have ~${remaining} characters to do so.`);
        }
      }

      if (name.length > maxResourceNameLength - 12) {
        if (name.length >= maxResourceNameLength - 2) {
          warnings.push(
            `With separators ("-"), you will not be able to include a stack and detail field for Google ${FirewallLabels.get(
              'firewalls',
            )}.`,
          );
        } else {
          const remaining = maxResourceNameLength - 2 - name.length;
          warnings.push(`If you plan to include a stack or detail field for Google ${FirewallLabels.get(
            'firewalls',
          )}, you will only
            have ~${remaining} characters to do so.`);
        }
      }
    }

    function validate(name) {
      const warnings = [];
      const errors = [];

      if (name && name.length) {
        validateSpecialCharacters(name, errors);
        validateLength(name, warnings, errors);
      }

      return {
        warnings: warnings,
        errors: errors,
      };
    }

    return {
      validate: validate,
    };
  })
  .run([
    'gceApplicationNameValidator',
    function (gceApplicationNameValidator) {
      ApplicationNameValidator.registerValidator('gce', gceApplicationNameValidator);
    },
  ]);
