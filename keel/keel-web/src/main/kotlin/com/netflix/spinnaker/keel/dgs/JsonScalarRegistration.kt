package com.netflix.spinnaker.keel.dgs

import graphql.schema.idl.RuntimeWiring

import com.netflix.graphql.dgs.DgsRuntimeWiring

import com.netflix.graphql.dgs.DgsComponent
import graphql.scalars.ExtendedScalars


@DgsComponent
class JsonScalarRegistration {
  @DgsRuntimeWiring
  fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
    return builder.scalar(ExtendedScalars.Json)
  }
}
