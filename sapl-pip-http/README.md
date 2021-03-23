```java
	@Configuration
	@ConditionalOnClass(io.sapl.pip.http.HttpPolicyInformationPoint.class)
	public static class HTTPConfiguration {
		@Nullable
		@Autowired(required = false)
		SslContext sslContext;

		@Bean
		public HttpPolicyInformationPoint httpPolicyInformationPoint() {
			if (sslContext == null) {
				log.info("HTTP PIP present. No SslContext bean. Loading with default SslContext...");
				return new HttpPolicyInformationPoint(new WebClientRequestExecutor());
			} else {
				log.info("HTTP PIP present. Loading with custom SslContext bean...");
				return new HttpPolicyInformationPoint(new WebClientRequestExecutor(sslContext));
			}
		}
	}
```