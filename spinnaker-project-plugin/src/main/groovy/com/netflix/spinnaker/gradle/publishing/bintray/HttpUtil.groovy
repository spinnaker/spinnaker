package com.netflix.spinnaker.gradle.publishing.bintray

class HttpUtil {

  final HttpURLConnection connection
  Response response

  HttpUtil(String url, String authHeader, String method) {
    connection = (HttpURLConnection) url.toURL().openConnection()
    connection.setRequestMethod(method)
    header("Authorization", authHeader)
  }

  HttpUtil header(String name, String value) {
    connection.setRequestProperty(name, value)
    this
  }

  HttpUtil uploadFile(File file, String contentType = "application/octet-stream") {
    upload(file.newInputStream(), contentType, file.size())
  }

  HttpUtil uploadJson(String json) {
    byte[] bytes = json.getBytes("UTF-8")
    upload(new ByteArrayInputStream(bytes), "application/json", bytes.length)
  }

  HttpUtil upload(InputStream source, String contentType, long size) {
    connection.setDoOutput(true)
    header("Content-Length", Long.toString(size))
    header("Content-Type", contentType)
    connection.getOutputStream().withCloseable { OutputStream os ->
      source.withCloseable { InputStream is ->
        byte[] buf = new byte[16 * 1024]
        int bytesRead
        while ((bytesRead = is.read(buf)) != -1) {
          os.write(buf, 0, bytesRead)
        }
        os.flush()
      }
    }
    this
  }

  Response getResponse() {
    if (response == null) {
      int httpStatus = connection.responseCode
      String message = connection.responseMessage
      String body = (httpStatus >= 400 ?
        connection.getErrorStream() :
        connection.getInputStream()).withCloseable { InputStream is ->
          is.text
      }
      response = new Response(httpStatus, message, body)
      connection.disconnect()
    }
    return response
  }

  static class Response {
    final int responseCode
    final String statusLine
    final String responseBody

    Response(int responseCode, String statusLine, String responseBody) {
      this.responseCode = responseCode
      this.statusLine = statusLine
      this.responseBody = responseBody
    }
  }
}
