///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.3.3

import dev.jbang.devkitman.*;

class listinstalled {
	public static void main(String[] args) {
		JdkManager jdkManager = JdkManager.create();
		jdkManager.listInstalledJdks().forEach(System.out::println);
	}
}
