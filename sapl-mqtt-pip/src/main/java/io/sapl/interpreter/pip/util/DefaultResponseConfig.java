package io.sapl.interpreter.pip.util;

import lombok.Data;

/**
 * These data objects store the default response configuration.
 */
@Data
@SuppressWarnings("ClassCanBeRecord") // for sapl usage the code must be compilable with jdk 11
public class DefaultResponseConfig {
    private final long defaultResponseTimeout;
    private final String defaultResponseType;
}
