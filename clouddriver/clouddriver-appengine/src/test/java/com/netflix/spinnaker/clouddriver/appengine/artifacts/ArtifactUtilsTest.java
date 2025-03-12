package com.netflix.spinnaker.clouddriver.appengine.artifacts;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ArtifactUtilsTest {

  @Test
  void testUntarStreamToPathWithEntryOutsideDestDirThrowsException() throws IOException {

    Exception ex = null;
    String s = "target/zip-unarchiver-slip-tests";
    File testZip = new File(new File("").getAbsolutePath(), "src/test/zip-slip/zip-slip.tar");
    File outputDirectory = new File(new File("test-tar").getAbsolutePath(), s);

    outputDirectory.delete();

    try {
      ArtifactUtils.untarStreamToPath(new FileInputStream(testZip), outputDirectory.getPath());
    } catch (Exception e) {
      ex = e;
    }

    assertNotNull(ex);
    assertTrue(ex.getMessage().startsWith("Entry is outside of the target directory"));
  }

  @Test
  void testUntarStreamDirDoesNotThrowsException() throws IOException {

    Exception ex = null;
    String s = "target/zip-unarchiver-slip-tests";
    File testZip = new File(new File("").getAbsolutePath(), "src/test/zip-slip/normal-tar.tar");
    File outputDirectory = new File(new File("test-tar").getAbsolutePath(), s);

    outputDirectory.delete();

    try {
      ArtifactUtils.untarStreamToPath(new FileInputStream(testZip), outputDirectory.getPath());
    } catch (Exception e) {
      ex = e;
    }

    assertNull(ex);
  }
}
