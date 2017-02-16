'use strict';


let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.fastProperties.view.tableRow.directive', [
  require('core/utils/jQuery.js'),
])
  .directive('fastPropertyTable', function ($timeout) {
    return {
      restrict: 'E',
      scope: {
        properties: '=',
        groupedBy: '=?',
        showDetails: '&',
      },
      link: function (scope, elem) {

        function renderInstances() {
          var properties = scope.properties;

          let tableHeader = `
          <table class="table table-hover" style="word-break: break-all">
          <thead style="border-top: none">
          <tr>
            <th width="20%" ng-if="scope.groupedBy !== 'property'">Property</th>
            <th width="20%">Value</th>
            <th width="10%" ng-if="scope.groupedBy !== 'app'">Application</th>
            <th width="5%">Env</th>
            <th width="10%">Region</th>
            <th width="10%">Stack</th>
            <th width="10%">Scope</th>
          </tr>
          </thead>
          <tbody>
          `;

          let tableFooter = `
            </tbody>
          </table>`;

          let innerHtml = tableHeader + properties.map(function(property) {

              return `

              <tr data-property-id="${property.propertyId}">
                <td>${property.key}</td>
                <td>${property.value || ''}</td>
                <td>${property.appId}</td>
                <td><span class="label label-default account-label account-label-${property.env}">${property.env}</span></td>
                <td>${property.scope.region}</td>
                <td>${property.scope.stack}</td>
                <td>${getBaseOfScope(property.scope)}</td>
              </tr>
              `;

            }).join('') + tableFooter;

          if (innerHtml !== elem.get(0).innerHTML) {
            elem.get(0).innerHTML = innerHtml;
          }
        }


        let getBaseOfScope = (scope) => {
          if (scope.serverId) { return scope.serverId; }
          if (scope.zone) { return scope.zone; }
          if (scope.asg) { return scope.asg; }
          if (scope.cluster) { return scope.cluster; }
          if (scope.stack) { return scope.stack; }
          if (scope.region) { return scope.region; }
          if (scope.appId) { return scope.appId; }
          if (scope.app) { return scope.app; }
          return 'GLOBAL';
        };

        elem.click(function(event) {
          $timeout(function() {
            let propertyId = event.target.getAttribute('data-property-id') || event.target.parentElement.getAttribute('data-property-id');
            scope.showDetails({propertyId: propertyId});
          });
        });

        scope.$on('$destroy', function() {
          elem.unbind('click');
        });

        scope.$watch('properties', renderInstances);
        scope.$watch('groupedBy', renderInstances);
      }
    };
  });

