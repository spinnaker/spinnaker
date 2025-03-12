package com.netflix.spinnaker.kork.plugins.remote.extension


import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionPayload
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionQuery
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionResponse
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionTransport
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isA

class RemoteExtensionTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test ("Get config type") {
      val result = subject.getTypedConfig<ConfigType>()
      expectThat(result).isA<ConfigType>()
    }

    test("Invoke is void") {
      val result = subject.invoke(remoteExtensionPayload)
      expectThat(result).isA<Unit>()
    }

    test("Returns the write response") {
      every { transport.write(any()) } returns writeResponse
      val result = subject.write<WriteResponse>(remoteExtensionPayload)
      expectThat(result).isA<WriteResponse>()
    }

    test("Returns the read response") {
      every { transport.read(any()) } returns readResponse
      val result = subject.read<ReadResponse>(remoteExtensionQuery)
      expectThat(result).isA<ReadResponse>()
    }
  }

  private class Fixture {
    val writeResponse = WriteResponse()
    val readResponse = ReadResponse()
    val remoteExtensionPayload = Payload()
    val remoteExtensionQuery = Query()
    val transport: RemoteExtensionTransport = mockk(relaxed = true)

    val subject: RemoteExtension = RemoteExtension(
      "remote.stage",
      "netflix.remote",
      "stage",
      ConfigType(),
      transport
    )
  }

  private class Payload: RemoteExtensionPayload
  private class Query: RemoteExtensionQuery
  private class ConfigType: RemoteExtensionPointConfig
  private class WriteResponse: RemoteExtensionResponse
  private class ReadResponse: RemoteExtensionResponse
}
