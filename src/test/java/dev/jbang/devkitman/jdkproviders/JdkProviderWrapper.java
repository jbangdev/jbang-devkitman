package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.JdkProvider;

public class JdkProviderWrapper implements JdkProvider {
	protected final JdkProvider provider;

	JdkProviderWrapper(JdkProvider provider) {
		this.provider = provider;
	}

	@Override
	public @NonNull JdkManager manager() {
		return provider.manager();
	}

	@Override
	public void manager(@NonNull JdkManager manager) {
		provider.manager(manager);
	}

	@Override
	public @NonNull String name() {
		return provider.name();
	}

	@Override
	public @NonNull String description() {
		return provider.description();
	}

	@Override
	public @NonNull List<Jdk> listInstalled() {
		return provider.listInstalled();
	}

	@Override
	public Jdk getInstalledById(@NonNull String id) {
		return provider.getInstalledById(id);
	}

	@Override
	public Jdk getInstalledByPath(@NonNull Path jdkPath) {
		return provider.getInstalledByPath(jdkPath);
	}

	@Override
	public @NonNull List<Jdk> listAvailable(String distros, Set<String> tags) {
		return provider.listAvailable(distros, tags);
	}

	@Override
	public Jdk getAvailableByIdOrToken(String idOrToken) {
		return provider.getAvailableByIdOrToken(idOrToken);
	}

	@Override
	public @NonNull Jdk install(@NonNull Jdk jdk) {
		return provider.install(jdk);
	}

	@Override
	public void uninstall(@NonNull Jdk jdk) {
		provider.uninstall(jdk);
	}
}
