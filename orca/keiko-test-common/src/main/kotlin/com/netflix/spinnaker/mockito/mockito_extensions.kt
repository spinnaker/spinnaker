package com.netflix.spinnaker.mockito

import org.mockito.stubbing.OngoingStubbing

infix fun <P1, R> OngoingStubbing<R>.doStub(stub: (P1) -> R): OngoingStubbing<R> =
  thenAnswer {
    it.arguments.run {
      @Suppress("UNCHECKED_CAST")
      stub.invoke(component1() as P1)
    }
  }

infix fun <P1, P2, R> OngoingStubbing<R>.doStub(stub: (P1, P2) -> R): OngoingStubbing<R> =
  thenAnswer {
    it.arguments.run {
      @Suppress("UNCHECKED_CAST")
      stub.invoke(component1() as P1, component2() as P2)
    }
  }

infix fun <P1, P2, P3, R> OngoingStubbing<R>.doStub(stub: (P1, P2, P3) -> R): OngoingStubbing<R> =
  thenAnswer {
    it.arguments.run {
      @Suppress("UNCHECKED_CAST")
      stub.invoke(component1() as P1, component2() as P2, component3() as P3)
    }
  }

/* ktlint-disable max-line-length */
infix fun <P1, P2, P3, P4, R> OngoingStubbing<R>.doStub(stub: (P1, P2, P3, P4) -> R): OngoingStubbing<R> =
  thenAnswer {
    it.arguments.run {
      @Suppress("UNCHECKED_CAST")
      stub.invoke(component1() as P1, component2() as P2, component3() as P3, component4() as P4)
    }
  }

infix fun <P1, P2, P3, P4, P5, R> OngoingStubbing<R>.doStub(stub: (P1, P2, P3, P4, P5) -> R): OngoingStubbing<R> =
  thenAnswer {
    it.arguments.run {
      @Suppress("UNCHECKED_CAST")
      stub.invoke(component1() as P1, component2() as P2, component3() as P3, component4() as P4, component5() as P5)
    }
  }
/* ktlint-enable max-line-length */
