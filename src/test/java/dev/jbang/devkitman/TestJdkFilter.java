package dev.jbang.devkitman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TestJdkFilter {
	private final Set<String> tags = new HashSet<>(Arrays.asList("Jdk", "Ga", "Javafx"));

	@Test
	void caseInsensitiveTags() {
		assertTrue(JdkFilter.tag("jdk").matches(tags));
		assertTrue(JdkFilter.tag("JDK").matches(tags));
		assertFalse(JdkFilter.tag("Jre").matches(tags));
	}

	@Test
	void inspectableNestedExpressions() {
		JdkFilter.Tag jdk = JdkFilter.tag("Jdk");
		JdkFilter.Not notJre = JdkFilter.not(JdkFilter.tag("Jre"));
		JdkFilter.Or runtime = JdkFilter.or(jdk, JdkFilter.tag("Jre"));
		JdkFilter.And expression = JdkFilter.and(runtime, notJre);
		assertSame(runtime, expression.filters().get(0));
		assertSame(jdk, runtime.filters().get(0));
		assertEquals("Jdk", jdk.tag());
		assertEquals(JdkFilter.Type.TAG, jdk.type());
		assertEquals(JdkFilter.Type.NOT, notJre.type());
		assertEquals(JdkFilter.Type.AND, expression.type());
		assertEquals(JdkFilter.Type.OR, runtime.type());
		assertEquals("Jre", ((JdkFilter.Tag) notJre.filter()).tag());
		assertTrue(expression.matches(tags));
		assertFalse(expression.matches(Collections.singleton("Jre")));
		assertFalse(JdkFilter.and(jdk, JdkFilter.tag("Native")).matches(tags));
		assertTrue(JdkFilter.not(JdkFilter.or()).matches(tags));
	}

	@Test
	void emptyJunctionsAndImmutability() {
		assertTrue(JdkFilter.and().matches(Collections.emptySet()));
		assertFalse(JdkFilter.or().matches(tags));
		JdkFilter[] children = { JdkFilter.tag("Jdk") };
		JdkFilter.And and = JdkFilter.and(children);
		children[0] = JdkFilter.tag("Jre");
		assertTrue(and.matches(tags));
		assertThrows(UnsupportedOperationException.class, () -> and.filters().clear());
		assertThrows(NullPointerException.class, () -> JdkFilter.tag(null));
		assertThrows(NullPointerException.class, () -> JdkFilter.not(null));
		assertThrows(NullPointerException.class, () -> JdkFilter.and((JdkFilter) null));
		assertThrows(NullPointerException.class, () -> JdkFilter.or((JdkFilter[]) null));
	}
}
