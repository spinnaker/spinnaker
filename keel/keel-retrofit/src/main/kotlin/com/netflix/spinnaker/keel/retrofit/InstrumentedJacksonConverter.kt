package com.netflix.spinnaker.keel.retrofit

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.netflix.spinnaker.kork.exceptions.SystemException
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.IOException
import java.lang.reflect.Type

class InstrumentedJacksonConverter(private val remoteName: String, private val adapter: ObjectReader) : Converter<ResponseBody, Any> {

  override fun convert(value: ResponseBody): Any? = value.use {
    it.string().let { body ->
      try {
        adapter.readValue<Any?>(body)
      } catch (ex: JsonMappingException) {
        throw UnparseableResponseException(remoteName, adapter.valueType, body, ex)
      }
    }
  }

  class Factory(private val remoteName: String, private val mapper: ObjectMapper) : Converter.Factory() {

    private val delegate = JacksonConverterFactory.create(mapper)

    override fun responseBodyConverter(
      type: Type,
      annotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<ResponseBody, *> {
      val javaType: JavaType = mapper.typeFactory.constructType(type)
      val reader: ObjectReader = mapper.readerFor(javaType)
      return InstrumentedJacksonConverter(remoteName, reader)
    }

    override fun requestBodyConverter(
      type: Type,
      parameterAnnotations: Array<out Annotation>,
      methodAnnotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<*, RequestBody> =
      delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)!!
  }
}

class UnparseableResponseException(
  val remoteName: String,
  val targetType: JavaType,
  val body: String,
  cause: JsonMappingException
) :
  SystemException("Cannot parse response from $remoteName to ${targetType.simpleSignature}, response body: $body", cause) {
    val targetSimpleSignature: String
      get() = targetType.simpleSignature
  }

private val JavaType.simpleSignature: String
  get() = StringBuilder(rawClass.simpleName).run {
    if (containedTypeCount() > 0) {
      append(containedTypes.joinToString(prefix = "<", transform = JavaType::simpleSignature, postfix = ">"))
    }
    toString()
  }

private val JavaType.containedTypes: List<JavaType>
  get() = (0 until containedTypeCount()).map(this::containedType)
