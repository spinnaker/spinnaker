package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.schema.Discriminator

abstract class Constraint(@Discriminator val type: String)
abstract class StatefulConstraint(type: String) : Constraint(type)
