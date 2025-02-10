# JBang Dev Kit Manager

This is a library that manages Java JDK versions on the local machine.
It can detect Java versions that are already available on the system
that have been installed by a variety of methods, this being default
system installers or 3rd party tools like SDKMan, Brew, Scoop, etc.
It can also download, install and manage JDKs itself.

## Usage

First include the library in your project:

### Maven

```xml
<dependency>
    <groupId>dev.jbang</groupId>
    <artifactId>jdkmanager</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.jbang:jdkmanager:0.1.0'
```

Then the simplest way to get started is:

```java
import dev.jbang.devkitman.*;

public class Main {
    public static void main(String[] args) {
        JDKManager jdkManager = JDKManager.create();
        Jdk jdk = jdkManager.getOrInstall("11+");
        System.out.println("JDK " + jdk.majorVersion() + " home folder " + jdk.getHome());
    }
}
```

This will either find a Java JDK on the system that is at least version 11,
or it will download and install a JDK that is at least version 11
(the actual version that gets installed depends on the "default version"
that is configured).
