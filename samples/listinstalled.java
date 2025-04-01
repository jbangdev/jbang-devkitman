///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.1.4

class listinstalled {
	public static void main(String[] args) {
		JdkManager jdkManager = JdkManager.create();
		System.out.println("Installed: " + jdkManager.listInstalledJdks());
	}
}
