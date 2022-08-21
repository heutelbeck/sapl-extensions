package io.sapl.axon.query;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class SeplTest {

	@Test
	void test() {
		var parser     = new SpelExpressionParser();
		var expression = parser.parseExpression("#message.length()");
		var ctx        = new StandardEvaluationContext();
		ctx.setVariable("message", "Hello, SePL!");
		try {
			var result = expression.getValue(ctx);
			log.info("->{}<-", result);

		} catch (Exception e) {
			log.error("ERR: {}", e.getMessage());
		}
	}

	@Test
	void test2() {
		var err = Mono.<String>error(new IllegalArgumentException()).onErrorResume(e -> {
			log.info("ZZZ");
			return Mono.error(new RuntimeException());
		}).flatMap(x -> {
			log.info("XXX");
			return Mono.just(x);
		}).log().block();
	}
}
