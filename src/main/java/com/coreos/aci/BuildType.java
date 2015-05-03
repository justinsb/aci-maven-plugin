package com.coreos.aci;

public enum BuildType {
  JAR, WAR;

  public String getDefaultBaseImage() {
    switch (this) {
    case WAR:
      return "aci.justinsb.com/jetty9";

    case JAR:
      return "aci.justinsb.com/java7";

    default:
      throw new IllegalStateException();
    }
  }
}
