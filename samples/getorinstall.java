///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.jbang:devkitman:0.1.4

class getorinstall {
	public static void main(String[] args) {
		JdkManager jdkManager = JdkManager.create();
		Jdk jdk = jdkManager.getOrInstallJdk(args.length > 0 ? args[0] : "11+");
		System.out.println("JDK: " + jdk);
	}
}
