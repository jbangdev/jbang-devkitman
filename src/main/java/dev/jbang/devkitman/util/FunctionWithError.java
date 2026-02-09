package dev.jbang.devkitman.util;

import java.io.IOException;

@FunctionalInterface
public interface FunctionWithError<IN, OUT> {
	OUT apply(IN in) throws IOException;

	default <NEXT> FunctionWithError<IN, NEXT> andThen(FunctionWithError<OUT, NEXT> next) {
		return in -> {
			OUT intermediate = this.apply(in);
			return next.apply(intermediate);
		};
	}
}
