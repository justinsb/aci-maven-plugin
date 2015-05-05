This is a standard jar maven project, but demonstrating the use of supporting jars.

Supporting jars are copied into /app/lib, and then automatically added to the classpath by the java ACI.

It was created with:

```
mvn archetype:generate -DgroupId=com.justinsb.aci -DartifactId=example-console2 -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

Then Guava was added to the maven dependencies in [pom.xml](pom.xml), and Guava was used to print the message.  Commons math was also added, just to show that
multiple JAR dependencies works!