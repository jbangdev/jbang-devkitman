///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.1.2
//JAVA 21+
//PREVIEW

import dev.jbang.devkitman.*;

void main(String[] args) {
	var jdkManager = JdkManager.create();
	var jdk = jdkManager.getOrInstallJdk(args.length > 0 ? args[0] : "11+");
	System.out.println("JDK " + jdk.majorVersion() + " home folder " + jdk.home());
}
