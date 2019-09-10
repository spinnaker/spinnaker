/*
// These will be used when we build out the Canary Config UI, so keeping them
// here for now.

import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Option } from 'react-select';
import FormRow from 'kayenta/layout/formRow';
import RadioChoice from 'kayenta/layout/radioChoice';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
*/
import { get } from 'lodash';
import { ICanaryMetricConfig } from 'kayenta/domain';

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');

