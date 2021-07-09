'use strict';

import { module } from 'angular';
import _ from 'lodash';
import { from as observableFrom, Subject } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { CloudMetricsReader } from '@spinnaker/core';

import './dimensionsEditor.component.less';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_DIMENSIONSEDITOR_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.dimensionEditor';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_DIMENSIONSEDITOR_COMPONENT; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_DIMENSIONSEDITOR_COMPONENT, []).component(
  'dimensionsEditor',
  {
    bindings: {
      alarm: '=',
      serverGroup: '=',
      updateAvailableMetrics: '&',
      namespaceUpdated: '=',
    },
    templateUrl: require('./dimensionsEditor.component.html'),
    controller: function () {
      this.viewState = {
        loadingDimensions: false,
      };

      this.fetchDimensionOptions = () => {
        this.viewState.loadingDimensions = true;
        const filters = { namespace: this.alarm.namespace };
        return observableFrom(
          CloudMetricsReader.listMetrics('aws', this.serverGroup.account, this.serverGroup.region, filters),
        );
      };

      const dimensionSubject = new Subject();

      dimensionSubject.pipe(switchMap(this.fetchDimensionOptions)).subscribe((results) => {
        this.viewState.loadingDimensions = false;
        results = results || [];
        results.forEach((r) => (r.dimensions = r.dimensions || []));
        this.dimensionOptions = _.uniq(_.flatten(results.map((r) => r.dimensions.map((d) => d.name)))).sort();
      });

      this.updateDimensionOptions = () => {
        dimensionSubject.next();
      };

      this.removeDimension = (index) => {
        this.alarm.dimensions.splice(index, 1);
        this.updateAvailableMetrics();
      };

      this.$onInit = () => {
        this.updateDimensionOptions();
        this.namespaceUpdated.subscribe(() => {
          this.updateDimensionOptions();
        });
      };
    },
  },
);
