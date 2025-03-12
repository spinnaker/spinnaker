package com.netflix.spinnaker.keel.ec2.jackson

import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.jackson.PropertyNamePolymorphicDeserializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jackson.JsonComponent

abstract class SecurityGroupRuleDeserializer :
  PropertyNamePolymorphicDeserializer<SecurityGroupRule>(SecurityGroupRule::class.java)
