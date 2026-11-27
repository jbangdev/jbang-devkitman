# JBang Dev Kit Manager

This is a library that manages Java JDK and JRE versions on the local machine.
It can detect Java versions that are already available on the system
that have been installed by a variety of methods, this being default
system installers or 3rd party tools like SDKMan, Brew, Scoop, etc.
It can also download, install and manage JDKs itself.

## Usage

First include the library in your project:

### JBang

```
//DEPS dev.jbang:jdkmanager:0.4.12
```

### Maven

```xml
<dependency>
	<groupId>dev.jbang</groupId>
	<artifactId>jdkmanager</artifactId>
	<version>0.4.12</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.jbang:devkitman:0.4.12'
```

Then the simplest way to get started is:

```java
//DEPS dev.jbang:devkitman:0.4.12
//DEPS org.apache.httpcomponents.client5:httpclient5-cache:5.6
import dev.jbang.devkitman.*;

public class Main {
	public static void main(String[] args) {
		var jdkManager = JdkManager.create();
		var jdk = jdkManager.getOrInstall("11+");
		System.out.println("JDK " + jdk.majorVersion() + " home folder " + jdk.getHome());
	}
}
```

This will either find a Java JDK on the system that is at least version 11,
or it will download and install a JDK that is at least version 11
(the actual version that gets installed depends on the "default version"
that is configured).

See [samples/](samples/) folder for more.

## Filtering JDKs and JREs

Queries without a filter remain JDK-only, including `getJdk`, `getOrInstallJdk`,
`listInstalledJdks`, and `listAvailableJdks`. Use an explicit `JdkFilter` to
allow standalone JREs when only a Java runtime is needed:

```java
JdkManager manager = JdkManager.create();
JdkFilter runtime = JdkFilter.or(JdkFilter.tag("Jdk"), JdkFilter.tag("Jre"));
Jdk.InstalledJdk java = manager.getOrInstallJdk("17+", runtime);
Jdk.InstalledJdk compiler = manager.getOrInstallJdk("17+"); // JDK only
List<Jdk.InstalledJdk> runtimes = manager.listInstalledJdks(runtime);
List<Jdk.AvailableJdk> jres = manager.listAvailableJdks(JdkFilter.tag("Jre"));
```

The same filter works for exact versions, open versions, provider IDs, and
`null` (the normal provider-precedence selection). Installed runtimes are
searched before downloading anything. Foojay and Metadata installers prefer a
matching JDK for a mixed JDK/JRE version request; an explicit `tag("Jre")`
can request a JRE download. JRE installations do not set global or versioned
JDK defaults, and setting a JRE as a JDK default is rejected.

Filters match tag names case-insensitively, including the names in
`Jdk.Default.Tags` (`Jdk`, `Jre`, `Graalvm`, `Native`, `Javafx`, `Ea`, `Ga`).
Compose them with `tag`, `not`, `and`, and `or`, for example:

```java
JdkFilter stableCompiler = JdkFilter.and(
	JdkFilter.tag("Jdk"), JdkFilter.not(JdkFilter.tag("Ea")));
Jdk jdk = manager.getJdk("21", stableCompiler,
	provider -> provider.name().equals("jbang"));
```

The existing provider-predicate overloads remain supported. Runtime filters
and provider predicates can be combined with the three-argument overloads of
`getJdk`, `getInstalledJdk`, and `getOrInstallJdk`.
`linkToExistingJdk(path, id, filter)` also allows explicitly linking a JRE.

### Provider and installer extension API

`JdkFilter` is an immutable, inspectable AST, not an opaque predicate.
`type()` identifies `TAG`, `NOT`, `AND`, or `OR`; `Tag.tag()`, `Not.filter()`,
and `And.filters()` / `Or.filters()` expose the expression's children.
`matches(jdk)` and `matches(tags)` provide in-memory evaluation. An empty
`and()` matches everything; an empty `or()` matches nothing.

Providers and installers may override `listInstalled(filter)`,
`listAvailable(filter)`, and the filtered version/ID/path queries to translate
the AST into efficient native requests. Existing implementations continue
working through default filtering adapters. Implementations that discover JREs
should expose them through the filtered overloads while keeping their no-filter
lists JDK-only. Available entries must carry accurate tags; installed entries
infer their runtime type from the Java installation.

These APIs are unreleased. From a consuming project's checkout, use a Gradle
composite build with the sibling devkitman checkout:

```powershell
.\gradlew.bat test --include-build ..\jbang-devkitman
```
