package com.netflix.spinnaker.mort.model

/**
 * A representation of an elastic ip
 */
interface ElasticIp {
  /**
   * The type of this elastic ip. May reference the cloud provider to which it is associated
   *
   * @return
   */
  String getType()

  /**
   * The public address associated with this elastic ip
   *
   * @return
   */
  String getAddress()

  /**
   * The identifier of the object that this elastic ip is attached to
   *
   * @return
   */
  String getAttachedToId()

  /**
   * The account associated with this elastic ip
   *
   * @return
   */
  String getAccountName()

  /**
   * The region associated with this elastic ip
   *
   * @return
   */
  String getRegion()
}
