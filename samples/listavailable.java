///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.4.2

import dev.jbang.devkitman.*;

class listavailable {
	public static void main(String[] args) {
		JdkManager jdkManager = JdkManager.create();
		jdkManager.listAvailableJdks().forEach(System.out::println);
	}
}
