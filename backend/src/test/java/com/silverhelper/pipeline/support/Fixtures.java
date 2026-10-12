package com.silverhelper.pipeline.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** backend/src/test/resources/fixtures/public-api/ 의 실제 응답 (P0-1b) */
public final class Fixtures {

	private Fixtures() {
	}

	public static String text(String name) {
		try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/public-api/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("픽스처 없음: " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static byte[] bytes(String name) {
		return text(name).getBytes(StandardCharsets.UTF_8);
	}

}
