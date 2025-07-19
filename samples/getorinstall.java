///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.2.0

import dev.jbang.devkitman.*;

class getorinstall {
	public static void main(String[] args) {
		JdkManager jdkManager = JdkManager.create();
		Jdk jdk = jdkManager.getOrInstallJdk(args.length > 0 ? args[0] : "11+");
		System.out.println("JDK: " + jdk);
	}
}
