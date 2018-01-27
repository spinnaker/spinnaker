package com.netflix.spinnaker.orca.libdiffs;

public class Diff {
  public String toString() {
    return displayDiff;
  }

  public Library getLibrary() {
    return library;
  }

  public void setLibrary(Library library) {
    this.library = library;
  }

  public String getDisplayDiff() {
    return displayDiff;
  }

  public void setDisplayDiff(String displayDiff) {
    this.displayDiff = displayDiff;
  }

  private Library library;
  private String displayDiff;
}
