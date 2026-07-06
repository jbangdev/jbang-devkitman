///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.4.10
//DEPS org.apache.httpcomponents.client5:httpclient5-cache:5.6

import dev.jbang.devkitman.*;

class listavailable {
	public static void main(String[] args) {
		JdkManager jdkManager = JdkManager.create();
		jdkManager.listAvailableJdks().forEach(System.out::println);
	}
}
