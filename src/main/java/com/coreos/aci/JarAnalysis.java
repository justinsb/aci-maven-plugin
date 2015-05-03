package com.coreos.aci;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;

import org.apache.maven.plugin.logging.Log;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

/**
 * Helper class to get information out of a JAR.
 */
public class JarAnalysis {
  final File jarFile;

  public JarAnalysis(File jarFile) {
    this.jarFile = jarFile;
  }

  /**
   * Finds the Main-Class attribute, or null if none set.
   * 
   * The Main-Class attribute makes a JAR runnable with "java -jar".
   */
  public String getManifestMainClass() throws IOException {
    String mainClass = null;
    try (ZipFile zipFile = new ZipFile(jarFile)) {
      ZipEntry manifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
      if (manifestEntry != null) {
        try (InputStream is = zipFile.getInputStream(manifestEntry)) {
          try (InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8)) {
            for (String line : CharStreams.readLines(isr)) {
              int colonIndex = line.indexOf(':');
              if (colonIndex == -1)
                continue;
              String key = line.substring(0, colonIndex);
              if (key.equals("Main-Class")) {
                mainClass = line.substring(colonIndex + 1).trim();
              }
            }
          }
        }
      }
      return mainClass;
    }
  }

  /**
   * Discover classes that have an entrypoint function
   * 
   * i.e. that declare void main(String[] args)
   */
  public List<String> discoverMainClasses(Log log) throws IOException {
    List<String> mainClasses = Lists.newArrayList();

    log.info("Scanning JAR for main classes: " + jarFile);

    try (ZipFile zipFile = new ZipFile(jarFile)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        if (!zipEntry.getName().endsWith(".class")) {
          continue;
        }
        log.debug("Reading class " + zipEntry);
        try (InputStream is = zipFile.getInputStream(zipEntry)) {
          DataInputStream dis = new DataInputStream(new BufferedInputStream(is));
          ClassFile classFile = new ClassFile(dis);
          for (Object methodObject : classFile.getMethods()) {
            MethodInfo method = (MethodInfo) methodObject;
            if ((method.getAccessFlags() & AccessFlag.STATIC) == 0)
              continue;
            if ((method.getAccessFlags() & AccessFlag.PUBLIC) == 0)
              continue;
            if (!method.getName().equals("main"))
              continue;
            if (!method.getDescriptor().equals("([Ljava/lang/String;)V"))
              continue;
            mainClasses.add(classFile.getName());
          }
        }
      }
    }
    return mainClasses;
  }
}
