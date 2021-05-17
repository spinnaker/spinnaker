package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Action

@JsonTypeInfo(
  use = Id.NAME,
  include = As.EXISTING_PROPERTY,
  property = "type"
)
@JsonSubTypes(
  Type(value = Action.ForwardAction::class, name = "forward"),
  Type(value = Action.RedirectAction::class, name = "redirect"),
  Type(value = Action.AuthenticateOidcAction::class, name = "authenticate-oidc")
)
interface ActionMixin
