package com.coreos.aci;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

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

      File mainArtifactFile = getMainArtifactFile();
      JarAnalysis jarAnalysis = new JarAnalysis(mainArtifactFile);
      String mainClass;
      try {
        mainClass = jarAnalysis.getManifestMainClass();
      } catch (IOException e) {
        throw new MojoExecutionException("Error reading JAR: " + mainArtifactFile, e);
      }

      if (mainClass != null) {
        // A runnable JAR
        return "/run";
      }

      try {
        List<String> mainClasses = jarAnalysis.discoverMainClasses(getLog());
        if (mainClasses.size() == 0) {
          getLog().warn("No classes with a `public static void main(String[] args)` method found");
        } else if (mainClasses.size() > 1) {
          getLog().warn("Multiple classes found with `public static void main(String[] args)` methods");
        } else {
          mainClass = mainClasses.get(0);
          getLog().info("Automatically chose main-class: " + mainClass);
        }
      } catch (IOException e) {
        throw new MojoExecutionException("Error reading JAR: " + mainArtifactFile, e);
      }

      if (mainClass == null) {
        throw new MojoExecutionException(
            "Must specify main class, either in the jar manifest, or in the mainClass configuration property.");
      }

      return "/run " + mainClass;
    }

    return "/run";
  }

  protected void addDefaultArtifacts(ContainerBuilder builder) throws IOException, MojoExecutionException {
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

    ContainerFile containerFile = new ContainerFile(file, imagePath);
    getLog().debug("Copying " + file + " to " + imagePath);
    containerFiles.add(containerFile);

    if (copyDependencies) {
      List<Artifact> runtimeDependencies = getDependencyArtifacts();
      for (Artifact runtimeDependency : runtimeDependencies) {
        file = runtimeDependency.getFile();
        imagePath = "/app/lib/" + file.getName();
        containerFile = new ContainerFile(file, imagePath);
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
      addDefaultArtifacts(builder);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy artifacts", e);
    }

    // copyResources(builder);

    String imageVersion = getAciVersion();

    File manifestFile = new File(projectBuildDirectory, "image.aci.manifest");
    try {
      builder.writeManifest(manifestFile, imageName, imageVersion);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to build manifest", e);
    }

    List<File> dependencies = Lists.newArrayList();
    for (ContainerFile containerFile : builder.getContainerFiles()) {
      dependencies.add(containerFile.sourcePath);
    }
    dependencies.add(manifestFile);

    if (isUpToDate(imageFile, dependencies)) {
      getLog().info("ACI is up to date");
    } else {
      getLog().debug("Building image " + imageName);
      try {
        builder.buildImage(manifestFile);
        getLog().info("Built ACI " + imageName + ":" + imageVersion);
      } catch (Exception e) {
        throw new MojoExecutionException("Failed to build image", e);
      }
    }

    return imageFile;
  }

  /**
   * Signs an ACI built by buildAci
   */
  protected byte[] signAci(File imageFile) throws MojoExecutionException {
    File signatureFile = new File(projectBuildDirectory, "image.aci.asc");

    byte[] signature;
    if (isUpToDate(signatureFile, Arrays.asList(imageFile))) {
      getLog().info("Signature is up to date");
      try {
        signature = Files.toByteArray(signatureFile);
        return signature;
      } catch (IOException e) {
        getLog().warn("Ignoring error reading existing signature file", e);
      }
    }
    AciSigner signer = new GpgCommandAciSigner();
    try {
      signature = signer.sign(imageFile);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Signing interrupted");
    }
    try {
      Files.write(signature, signatureFile);
    } catch (IOException e) {
      throw new MojoExecutionException("Error writing signature file", e);
    }

    return signature;
  }

  private boolean isUpToDate(File target, Iterable<File> dependencies) {
    if (!target.exists()) {
      return false;
    }

    long targetDate = target.lastModified();

    // Allow for FS timestamps that are not accurately stored
    targetDate -= 30 * 1000;

    for (File dependency : dependencies) {
      long dependencyDate = dependency.lastModified();
      if (dependencyDate >= targetDate) {
        getLog().debug("File is out of date: " + target + " due to " + dependency);
        return false;
      }
    }
    return true;
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
