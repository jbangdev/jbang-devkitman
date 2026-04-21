package dev.jbang.devkitman;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NonNull;

public interface JdkDistroQuery {
	@NonNull
	default List<JdkDistro> listDistros() {
		throw new UnsupportedOperationException(
				"Listing available distros is not supported by " + getClass().getName());
	}

	class JdkDistro {
		private final String name;

		public JdkDistro(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		@Override
		public final boolean equals(Object o) {
			if (!(o instanceof JdkDistro))
				return false;

			JdkDistro jdkDistro = (JdkDistro) o;
			return Objects.equals(name, jdkDistro.name);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(name);
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
