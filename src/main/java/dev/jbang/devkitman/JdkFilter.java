package dev.jbang.devkitman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An inspectable, immutable tag expression. Tag matching is case insensitive.
 * Providers may translate the expression to their own query language.
 */
public interface JdkFilter {
	JdkFilter JDK = tag(Jdk.Default.Tags.Jdk.name());

	enum Type {
		TAG, NOT, AND, OR
	}

	Type type();

	boolean matches(Set<String> tags);

	default boolean matches(Jdk jdk) {
		return matches(jdk.tags());
	}

	static Tag tag(String tag) {
		return new Tag(tag);
	}

	static Not not(JdkFilter filter) {
		return new Not(filter);
	}

	static And and(JdkFilter... filters) {
		return new And(filters);
	}

	static Or or(JdkFilter... filters) {
		return new Or(filters);
	}

	final class Tag implements JdkFilter {
		private final String tag;

		private Tag(String tag) {
			this.tag = Objects.requireNonNull(tag, "tag");
		}

		public String tag() {
			return tag;
		}

		@Override
		public Type type() {
			return Type.TAG;
		}

		@Override
		public boolean matches(Set<String> tags) {
			return tags.stream().anyMatch(tag::equalsIgnoreCase);
		}
	}

	final class Not implements JdkFilter {
		private final JdkFilter filter;

		private Not(JdkFilter filter) {
			this.filter = Objects.requireNonNull(filter, "filter");
		}

		public JdkFilter filter() {
			return filter;
		}

		@Override
		public Type type() {
			return Type.NOT;
		}

		@Override
		public boolean matches(Set<String> tags) {
			return !filter.matches(tags);
		}
	}

	abstract class Junction implements JdkFilter {
		private final List<JdkFilter> filters;

		private Junction(JdkFilter[] filters) {
			List<JdkFilter> copy = new ArrayList<>(Arrays.asList(filters));
			copy.forEach(filter -> Objects.requireNonNull(filter, "filter"));
			this.filters = Collections.unmodifiableList(copy);
		}

		public List<JdkFilter> filters() {
			return filters;
		}
	}

	final class And extends Junction {
		private And(JdkFilter[] filters) {
			super(filters);
		}

		@Override
		public Type type() {
			return Type.AND;
		}

		@Override
		public boolean matches(Set<String> tags) {
			return filters().stream().allMatch(filter -> filter.matches(tags));
		}
	}

	final class Or extends Junction {
		private Or(JdkFilter[] filters) {
			super(filters);
		}

		@Override
		public Type type() {
			return Type.OR;
		}

		@Override
		public boolean matches(Set<String> tags) {
			return filters().stream().anyMatch(filter -> filter.matches(tags));
		}
	}
}
