package dev.jbang.devkitman.jdkproviders;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.JdkProvider;

public abstract class BaseJdkProvider implements JdkProvider {
	protected JdkManager manager;

	@Override
	public @NonNull JdkManager manager() {
		return manager;
	}

	public void manager(@NonNull JdkManager manager) {
		this.manager = manager;
	}
}
