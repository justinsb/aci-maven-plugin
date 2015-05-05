## Maven plugin options

For many maven projects the default options will be correct, but you can customize the options by
including the aci plugin in your pom.xml.

Here is an example from [example-console2/pom.xml](../examples/example-console2/pom.xml):

```
  <build>
    <plugins>
      <plugin>
        <groupId>com.coreos</groupId>
        <artifactId>aci-maven-plugin</artifactId>
        <version>0.1-SNAPSHOT</version>
        <configuration>
          <imageName>aci.justinsb.com/example-console2</imageName>
          <repository>s3://aci.justinsb.com</repository>
          <mainClass>com.justinsb.aci.App</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
```


Configuration options are:

* `imageName` the name of the ACI to create.  Defaults to the `<groupId>/<artifactId>` for the project
* `repository` the URL for the ACI repo to push to.  Defaults to `s3://<groupId>`
* `baseImage` the base image to use.  Defaults to `aci.justinsb.com/java7` for a JAR project,
or `aci.justinsb.com/jetty9` for a WAR project.
* `cmd` the command to run.  Defaults to `/run` or `/run <mainClass>` if a main class is specified.
* `mainClass` the main class to run.  Not needed for a WAR project.  For a JAR, the plugin will
check the manifest to see if a main class has been specified, and use that.  If not, then it will
scan the JAR for classes with a `main` function; if exactly one is found it will use that.
