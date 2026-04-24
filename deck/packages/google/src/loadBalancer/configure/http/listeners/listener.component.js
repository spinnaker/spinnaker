'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { GCE_ADDRESS_SELECTOR } from '../../common/addressSelector.component';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT =
  'spinnaker.deck.gce.httpLoadBalancer.listener.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT, [GCE_ADDRESS_SELECTOR]).component(
  'gceListener',
  {
    bindings: {
      command: '=',
      listener: '=',
      deleteListener: '&',
      index: '=',
      application: '=',
    },
    templateUrl: require('./listener.component.html'),
    controller: function () {
      this.$onInit = () => {
        this.certificates = this.command.backingData.certificates;
        const loadBalancerMap = this.command.backingData.loadBalancerMap;

        this.getName = (listener, applicationName) => {
          const listenerName = [applicationName, listener.stack || '', listener.detail || ''].join('-');
          return _.trimEnd(listenerName, '-');
        };

        this.getCertificates = () => {
          return this.command.backingData.certificates
            .filter((certificate) => certificate.account === this.command.loadBalancer.credentials)
            .map((certificate) => certificate.name);
        };

        this.getSubnets = () => {
          const ret = this.command.backingData.subnetMap[this.command.loadBalancer.network]
            .filter((subnet) => subnet.region === this.command.loadBalancer.region)
            .map((subnet) => subnet.name);
          return _.uniq(ret);
        };

        this.getInternalAddresses = () => {
          return this.command.backingData.addresses.filter(
            (address) =>
              address.addressType === 'INTERNAL' && address.subnetwork.split('/').pop() === this.listener.subnet,
          );
        };

        this.updateName = (listener, appName) => {
          listener.name = this.getName(listener, appName);
        };

        this.localListenerHasSameName = () => {
          return (
            this.command.loadBalancer.listeners.filter((listener) => listener.name === this.listener.name).length > 1
          );
        };

        this.existingListenerNames = () => {
          return _.get(loadBalancerMap, [this.command.loadBalancer.credentials, 'listeners']);
        };

        this.isHttps = (port) => port === 443 || port === '443';
        // certificateMap writes are only supported on global external/classic HTTPS proxies (HTTP
        // type). INTERNAL_MANAGED regional proxies do not support certificateMap per GCP docs.
        this.supportsCertificateMap = () => this.command.loadBalancer.loadBalancerType === 'HTTP';
        this.certificateMapPattern = /^[a-z]([-a-z0-9]*[a-z0-9])?$/;
        // Extracts the short resource name from a certificateMap value. Users may paste a full
        // Certificate Manager URL (e.g. //certificatemanager.googleapis.com/projects/p/locations/
        // global/certificateMaps/my-map); only the last path segment (the map name) is stored.
        // The server reconstructs the full URL using GCEUtil.buildCertificateMapUrl.
        this.getCertificateMapName = (certificateMap) => {
          if (!_.isString(certificateMap)) {
            return certificateMap;
          }
          const normalized = certificateMap.trim();
          return normalized ? _.last(normalized.split('/')) : normalized;
        };

        // Reconcile the XOR between certificate and certificateMap on a listener. Ensures only
        // one is active at a time. Also infers certificateSource for existing listeners that were
        // created before the certificateSource field existed (backward compat).
        this.syncCertificateState = (listener) => {
          if (!this.supportsCertificateMap()) {
            listener.certificateSource = 'certificate';
            listener.certificateMap = null;
            return;
          }

          if (!listener.certificateSource) {
            listener.certificateSource = listener.certificateMap ? 'certificateMap' : 'certificate';
          }

          if (listener.certificateSource === 'certificateMap') {
            listener.certificate = null;
            if (_.isString(listener.certificateMap)) {
              listener.certificateMap = this.getCertificateMapName(listener.certificateMap);
            }
          } else {
            listener.certificateMap = null;
          }
        };

        this.onCertificateSourceChanged = (listener) => {
          if (!this.supportsCertificateMap()) {
            listener.certificateSource = 'certificate';
            listener.certificateMap = null;
            return;
          }

          if (listener.certificateSource === 'certificateMap') {
            listener.certificate = null;
          } else {
            listener.certificateMap = null;
          }
        };

        this.onCertificateSelected = (listener) => {
          if (listener.certificate) {
            listener.certificateSource = 'certificate';
            listener.certificateMap = null;
          }
        };

        this.onCertificateMapChanged = (listener) => {
          if (_.isString(listener.certificateMap)) {
            listener.certificateMap = this.getCertificateMapName(listener.certificateMap);
          }
          if (listener.certificateMap) {
            listener.certificateSource = 'certificateMap';
            listener.certificate = null;
          }
        };

        this.onPortChanged = (listener) => {
          if (!this.isHttps(listener.port)) {
            listener.certificate = null;
            listener.certificateMap = null;
            listener.certificateSource = 'certificate';
          } else {
            this.syncCertificateState(listener);
          }
        };

        if (!this.listener.name) {
          this.updateName(this.listener, this.application.name);
        }

        this.onPortChanged(this.listener);

        this.onAddressSelect = (address) => {
          if (address) {
            this.listener.ipAddress = address.address;
          } else {
            this.listener.ipAddress = null;
          }
        };
      };
    },
  },
);
