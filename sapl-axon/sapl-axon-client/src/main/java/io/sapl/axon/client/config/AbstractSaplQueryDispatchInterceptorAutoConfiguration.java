package io.sapl.axon.client.config;

import io.sapl.axon.client.metadata.DefaultSaplQueryInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(value = { DefaultSaplQueryInterceptor.class })
public class AbstractSaplQueryDispatchInterceptorAutoConfiguration {
}
