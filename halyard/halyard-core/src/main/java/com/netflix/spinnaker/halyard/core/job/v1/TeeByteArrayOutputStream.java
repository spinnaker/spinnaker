/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.core.job.v1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TeeByteArrayOutputStream extends ByteArrayOutputStream {
  private final OutputStream tee;

  TeeByteArrayOutputStream(OutputStream tee) {
    this.tee = tee;
  }

  @Override
  public synchronized void write(int b) {
    super.write(b);
    try {
      tee.write(b);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void write(byte b[], int off, int len) {
    super.write(b, off, len);
    try {
      tee.write(b, off, len);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    super.write(b);
    tee.write(b);
  }

  @Override
  public void flush() throws IOException {
    super.flush();
    tee.flush();
  }

  @Override
  public void close() throws IOException {
    super.close();
    tee.close();
  }
}
