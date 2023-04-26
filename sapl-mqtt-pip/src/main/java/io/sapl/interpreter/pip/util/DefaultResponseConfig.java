package io.sapl.interpreter.pip.util;

import lombok.Data;

/**
 * These data objects store the default response configuration.
 */
@Data
public class DefaultResponseConfig {
	private final long   defaultResponseTimeout;
	private final String defaultResponseType;
}
