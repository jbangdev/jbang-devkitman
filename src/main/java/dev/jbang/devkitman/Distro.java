package dev.jbang.devkitman;

public class Distro {
	public final String name;
	public final boolean isGraalVM;

	public Distro(String name, boolean isGraalVM) {
		this.name = name;
		this.isGraalVM = isGraalVM;
	}
}
