You can configure com.coreos as a well-known plugin prefix:

Edit ~/.m2/settings.xml

Add
<pluginGroups>
  <pluginGroup>com.coreos</pluginGroup>
</pluginGroups>


See docs/settings.xml


Invoking ... configure the goal your want to run (build/sign/push) in pom.xml:


    <plugins>
      <plugin>
        <groupId>com.coreos</groupId>
        <artifactId>aci-maven-plugin</artifactId>
        <version>0.1-SNAPSHOT</version>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
            </goals>
          </execution>
        </executions>
      </plugin>


For a standalone JAR, you need to specify the main class:

<plugins>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <configuration>
          <archive>
            <manifest>
              <mainClass>aci.justinsb.com.App</mainClass>
            </manifest>
          </archive>
        </configuration>
      </plugin>

    </plugins>