package com.coreos.aci;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;
import com.coreos.appc.AciImageInfo;
import com.coreos.appc.AciRepository;
import com.coreos.appc.AciSigner;
import com.coreos.appc.AppcContainerBuilder;
import com.coreos.appc.ContainerBuilder;
import com.coreos.appc.ContainerFile;
import com.coreos.appc.GpgCommandAciSigner;
import com.coreos.appc.S3AciRepository;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.primitives.Chars;

/**
 * Common functionality for ACI mojos
 * 
 */
public abstract class BaseAciMojo extends BaseMojo {

  /** The base image to use. */
  @Parameter(property = "baseImage")
  private String baseImage;

  /** The created image will be given this name. */
  @Parameter(property = "imageName")
  private String imageName;

  /** The cmd command for the image. */
  @Parameter(property = "cmd")
  private String cmd;

  /** The main class command for the JAR. Used if cmd is not specified. */
  @Parameter(property = "mainClass")
  private String mainClass;

  /**
   * The ACI repository to push to. Must be set for a push.
   */
  @Parameter(property = "repository")
  protected String aciRepository;

  protected AciRepository getAciRepository() throws MojoExecutionException {
    if (Strings.isNullOrEmpty(this.aciRepository)) {
      throw new MojoExecutionException("Must set repository to push to");
    }
    URI uri = URI.create(this.aciRepository);
    String scheme = uri.getScheme();
    if (scheme.equals("s3")) {
      AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
      AmazonS3Client amazonS3Client = new AmazonS3Client(provider);
      String bucketName = uri.getHost();
      String prefix = uri.getPath();
      return new S3AciRepository(amazonS3Client, bucketName, prefix);
    } else {
      throw new MojoExecutionException("Unknown repository scheme: " + scheme);
    }
  }

  protected BuildType detectBuildType() throws MojoExecutionException {
    Artifact mainArtifact = getMainArtifact();
    String artifactType = mainArtifact.getType();
    if ("war".equals(artifactType)) {
      return BuildType.WAR;
    } else if ("jar".equals(artifactType)) {
      return BuildType.JAR;
    } else {
      getLog().warn("Unknown artifact type: " + artifactType);
      return null;
    }
  }

  protected void validateParameters() throws MojoExecutionException {
    BuildType buildType = detectBuildType();

    if (Strings.isNullOrEmpty(baseImage)) {
      if (buildType != null) {
        baseImage = buildType.getDefaultBaseImage();
      } else {
        throw new MojoExecutionException("Must specify baseImage");
      }
    }

    if (Strings.isNullOrEmpty(imageName)) {
      Artifact mainArtifact = getMainArtifact();
      imageName = mainArtifact.getGroupId() + "/" + mainArtifact.getArtifactId();
      // throw new MojoExecutionException("Must specify imageName");
    }

    if (Strings.isNullOrEmpty(cmd)) {
      cmd = getDefaultCommand(baseImage);

      if (Strings.isNullOrEmpty(cmd)) {
        throw new MojoExecutionException("Must specify cmd");
      }
    }
  }

  protected String getDefaultCommand(String baseImage) throws MojoExecutionException {
    BuildType buildType = detectBuildType();
    if (BuildType.JAR == buildType) {
      if (!Strings.isNullOrEmpty(this.mainClass)) {
        return "/run " + this.mainClass;
      }

      String mainClass = null;
      File mainArtifactFile = getMainArtifactFile();
      try (ZipFile zipFile = new ZipFile(mainArtifactFile)) {
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
      } catch (IOException e) {
        throw new MojoExecutionException("Error opening JAR: " + mainArtifactFile, e);
      }

      if (mainClass == null) {
        throw new MojoExecutionException(
            "Must specify main class, either in the jar manifest, or in the mainClass configuration property.");
      }

      return "/run"; // Will run JAR
    }

    return "/run";
  }

  protected void copyDefaultArtifacts(ContainerBuilder builder) throws IOException, MojoExecutionException {
    List<ContainerFile> containerFiles = new ArrayList<>();

    File file = getMainArtifactFile();
    String targetName = file.getName();

    boolean copyDependencies = false;
    BuildType buildType = detectBuildType();
    if (buildType == null) {
      throw new MojoExecutionException("Cannot automatically copy artifacts; build type unknown");
    }

    switch (buildType) {
    case WAR:
      targetName = "root.war";
      break;

    case JAR:
      targetName = "app.jar";
      copyDependencies = true;
      break;

    default:
      throw new MojoExecutionException("Unknown build type: " + buildType);
    }

    String imagePath = "/app/" + targetName;

    ContainerFile containerFile = new ContainerFile(file.toPath(), imagePath);
    getLog().debug("Copying " + file + " to " + imagePath);
    containerFiles.add(containerFile);

    if (copyDependencies) {
      List<Artifact> runtimeDependencies = getDependencyArtifacts();
      for (Artifact runtimeDependency : runtimeDependencies) {
        file = runtimeDependency.getFile();
        imagePath = "/app/lib/" + file.getName();
        containerFile = new ContainerFile(file.toPath(), imagePath);
        containerFiles.add(containerFile);
      }
    }

    builder.addFiles(containerFiles);
  }

  private File getMainArtifactFile() throws MojoExecutionException {
    Artifact mainArtifact = getMainArtifact();
    File file = mainArtifact.getFile();
    if (file == null) {
      throw new MojoExecutionException("No file found for primary artifact (" + mainArtifact + ")");
    }
    return file;
  }

  protected String getAciVersion() {
    String aciVersion = mavenProject.getVersion();
    return aciVersion;
  }

  protected File buildAci() throws MojoExecutionException {

    File imageFile = new File(projectBuildDirectory, "image.aci");
    ContainerBuilder builder = new AppcContainerBuilder(imageFile);
    builder.log = getSlf4jLogger();
    builder.baseImage = baseImage;
    builder.cmd = cmd;
    // builder.env = env;
    // builder.entryPoint = entryPoint;
    // builder.exposesSet = exposesSet;
    // builder.maintainer = maintainer;

    try {
      copyDefaultArtifacts(builder);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy artifacts", e);
    }

    // copyResources(builder);

    try {
      builder.buildImage(imageName, getAciVersion());
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to build image", e);
    }

    return imageFile;
  }

  /**
   * Signs an ACI built by buildAci
   */
  protected byte[] signAci(File imageFile) throws MojoExecutionException {

    AciSigner signer = new GpgCommandAciSigner();
    byte[] signature;
    try {
      signature = signer.sign(imageFile);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Signing interrupted");
    }
    File signatureFile = new File(projectBuildDirectory, "image.aci.asc");
    try {
      Files.write(signature, signatureFile);
    } catch (IOException e) {
      throw new MojoExecutionException("Error writing signature file", e);
    }

    return signature;
  }

  /**
   * Signs an ACI built by buildAci
   */
  protected void pushAci(File imageFile, byte[] signature) throws MojoExecutionException {
    AciImageInfo imageInfo = new AciImageInfo();
    imageInfo.name = imageName;
    imageInfo.version = getAciVersion();

    AciRepository aciRepository = getAciRepository();

    aciRepository.push(imageInfo, imageFile, signature, getSlf4jLogger());
  }
}
