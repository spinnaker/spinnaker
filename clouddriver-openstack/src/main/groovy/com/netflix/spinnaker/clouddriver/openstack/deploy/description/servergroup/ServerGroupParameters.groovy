/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup

import com.fasterxml.jackson.annotation.JsonCreator
import groovy.transform.AutoClone
import groovy.transform.Canonical

/**
 * This class is a wrapper for parameters that are passed to an openstack heat template
 * when auto scaling groups are created.
 */
@AutoClone
@Canonical
class ServerGroupParameters {

  String instanceType
  String image
  Integer internalPort
  Integer maxSize
  Integer minSize
  Integer desiredSize
  String networkId
  String subnetId
  List<String> loadBalancers
  List<String> securityGroups
  AutoscalingType autoscalingType
  Scaler scaleup
  Scaler scaledown

  Map<String, String> toParamsMap() {
    [
      flavor              : instanceType,
      image               : image,
      max_size            : maxSize ? maxSize.toString() : null,
      min_size            : minSize ? minSize.toString() : null,
      desired_size        : desiredSize ? desiredSize.toString() : null,
      network_id          : networkId,
      subnet_id           : subnetId,
      load_balancers      : loadBalancers ? loadBalancers.join(',') : null,
      security_groups     : securityGroups ? securityGroups.join(',') : null,
      autoscaling_type    : autoscalingType ? autoscalingType.toString() : null,
      scaleup_cooldown    : scaleup?.cooldown ? scaleup.cooldown.toString() : null,
      scaleup_adjustment  : scaleup?.adjustment ? scaleup.adjustment.toString() : null,
      scaleup_period      : scaleup?.period ? scaleup.period.toString() : null,
      scaleup_threshold   : scaleup?.threshold ? scaleup.threshold.toString() : null,
      scaledown_cooldown  : scaledown?.cooldown ? scaledown.cooldown.toString() : null,
      scaledown_adjustment: scaledown?.adjustment ? scaledown.adjustment.toString() : null,
      scaledown_period    : scaledown?.period ? scaledown.period.toString() : null,
      scaledown_threshold : scaledown?.threshold ? scaledown.threshold.toString() : null,
    ]
  }

  static ServerGroupParameters fromParamsMap(Map<String, String> params) {
    new ServerGroupParameters(
      instanceType: params.get('flavor'),
      image: params.get('image'),
      maxSize: params.get('max_size')?.toInteger(),
      minSize: params.get('min_size')?.toInteger(),
      desiredSize: params.get('desired_size')?.toInteger(),
      networkId: params.get('network_id'),
      subnetId: params.get('subnet_id'),
      loadBalancers: unescapePythonUnicodeJsonList(params.get('load_balancers')),
      securityGroups: unescapePythonUnicodeJsonList(params.get('security_groups')),
      autoscalingType: params.get('autoscaling_type') ? AutoscalingType.fromString(params.get('autoscaling_type')) : null,
      scaleup: new Scaler(
        cooldown: params.get('scaleup_cooldown')?.toInteger(),
        adjustment: params.get('scaleup_adjustment')?.toInteger(),
        period: params.get('scaleup_period')?.toInteger(),
        threshold: params.get('scaleup_threshold')?.toInteger()
      ),
      scaledown: new Scaler(
        cooldown: params.get('scaledown_cooldown')?.toInteger(),
        adjustment: params.get('scaledown_adjustment')?.toInteger(),
        period: params.get('scaledown_period')?.toInteger(),
        threshold: params.get('scaledown_threshold')?.toInteger()
      )
    )
  }

  /**
   * Stack parameters of type 'comma_delimited_list' come back as a unicode json string. We need to split that up.
   *
   * I need a shower.
   *
   * @param string
   * @return
   */
  static List<String> unescapePythonUnicodeJsonList(String string) {
    string?.split(",")?.collect { s ->
      s.replace("u'", "")
        .replace("'","")
        .replace("[","")
        .replace("]","")
        .replaceAll("([ ][ ]*)","")
    } ?: []
  }

  /**
   * Scaleup/scaledown parameters for a server group
   */
  @AutoClone
  @Canonical
  static class Scaler {
    Integer cooldown
    Integer adjustment
    Integer period
    Integer threshold
  }

  /**
   * CPU: average cpu utilization across server group. meter name is cpu_util.
   * NETWORK_INCOMING: average incoming bytes/second across server group. meter name is network.incoming.bytes.rate
   * NETWORK_OUTGOING: average outgoing bytes/second across server group. meter name is network.outgoing.bytes.rate
   */
  static enum AutoscalingType {
    CPU('cpu_util'), NETWORK_INCOMING('network.incoming.bytes.rate'), NETWORK_OUTGOING('network.outgoing.bytes.rate')

    String meterName

    AutoscalingType(String meterName) {
      this.meterName = meterName
    }

    @Override
    String toString() {
      meterName
    }

    @JsonCreator
    static String fromMeter(String meter) {
      switch (meter) {
        case CPU.meterName:
          CPU.name().toLowerCase()
          break
        case NETWORK_INCOMING.meterName:
          NETWORK_INCOMING.name().toLowerCase()
          break
        case NETWORK_OUTGOING.meterName:
          NETWORK_OUTGOING.name().toLowerCase()
          break
        default:
          throw new IllegalArgumentException("Invalid enum meter name: $meter")
      }
    }

    static AutoscalingType fromString(String value) {
      switch (value) {
        case CPU.toString():
          CPU
          break
        case NETWORK_INCOMING.toString():
          NETWORK_INCOMING
          break
        case NETWORK_OUTGOING.toString():
          NETWORK_OUTGOING
          break
        default:
          throw new IllegalArgumentException("Invalid enum meter name: $value")
      }
    }

  }

}
