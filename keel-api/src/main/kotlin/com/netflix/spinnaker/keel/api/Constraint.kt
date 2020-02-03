package com.netflix.spinnaker.keel.api

abstract class Constraint(val type: String)
abstract class StatefulConstraint(type: String) : Constraint(type)
