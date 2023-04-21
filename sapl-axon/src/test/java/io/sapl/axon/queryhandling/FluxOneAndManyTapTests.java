package io.sapl.axon.queryhandling;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
class FluxOneAndManyTapTests {

	Consumer<Subscription>      onSubscribe;
	Consumer<SignalType>        doFinally;
	Flux<AuthorizationDecision> source;

	@BeforeEach
	void beforeEach() {
		onSubscribe = mock(Consumer.class);
		doFinally   = mock(Consumer.class);
		source      = Flux
				.just(AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE,
						AuthorizationDecision.NOT_APPLICABLE, AuthorizationDecision.DENY)
				.delayElements(Duration.ofMillis(500L)).doOnSubscribe(onSubscribe).doFinally(doFinally);
	}

	@Test
	void when_oneDecisionIsCalledTwice_then_throw() throws InterruptedException {
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(50L));

		assertThrows(IllegalStateException.class, () -> {
			tap.one();
			tap.one();
		});
		verify(onSubscribe, times(0)).accept(any());
		verify(doFinally, times(0)).accept(any());
	}

	@Test
	void when_oneDecisionIsSunbscribedToTwice_then_throw() throws InterruptedException {
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(50L));

		var one = tap.one();
		one.subscribe();
		StepVerifier.create(one).expectError(IllegalStateException.class).verify();
	}

	@Test
	void when_decisionsIsCalledTwice_then_throw() throws InterruptedException {
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(50L));

		assertThrows(IllegalStateException.class, () -> {
			tap.many();
			tap.many();
		});
		verify(onSubscribe, times(0)).accept(any());
		verify(doFinally, times(0)).accept(any());
	}

	@Test
	void when_manyDecisionIsSunbscribedToTwice_then_throw() throws InterruptedException {
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(50L));

		var many = tap.many();
		many.take(1).blockLast();
		StepVerifier.create(many).expectError(IllegalStateException.class).verify();
	}

	@Test
	void when_oneThenManyNoDelay_then_AllEventsAreConsumed() throws InterruptedException {
		var ttl = 200L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.PERMIT)
				.verifyComplete();
		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.PERMIT, Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY)
				.verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(1)).accept(any());
		verify(doFinally, times(1)).accept(any());
	}

	@Test
	void when_oneThenManyDelayButNotTillAfterNextUpdate_then_AllEventsAreConsumed() throws InterruptedException {
		var ttl = 500L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.PERMIT)
				.verifyComplete();
		Thread.sleep(200L); // shorter than event delay and ttl
		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.PERMIT, Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY)
				.verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(1)).accept(any());
		verify(doFinally, times(1)).accept(any());
	}

	@Test
	void when_oneThenManyDelayLongerThanUpdateDelayButShorterThanTTL_then_decisionsDoesGet2ndEventFirst()
			throws InterruptedException {
		var ttl = 1500L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.PERMIT)
				.verifyComplete();
		Thread.sleep(750); // longer than event delay shorter than ttl
		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY).verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(1)).accept(any());
		verify(doFinally, times(1)).accept(any());
	}

	@Test
	void when_oneThenManyDelayLongerThanUpdateDelayAndTTL_then_decisionsGetAllEventsAsItIsANewSubscription_and_PDPisSubscribedToTwice()
			throws InterruptedException {
		var ttl = 750L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.PERMIT)
				.verifyComplete();
		Thread.sleep(1000L); // longer than event delay shorter than ttl
		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.PERMIT, Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY)
				.verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(2)).accept(any());
		verify(doFinally, times(2)).accept(any());
	}

	@Test
	void when_manyThenOneNoDelay_then_AllEventsAreConsumedOneGetsFinalOne() throws InterruptedException {
		var ttl = 200L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.PERMIT, Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY)
				.verifyComplete();
		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.DENY)
				.verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(1)).accept(any());
		verify(doFinally, times(1)).accept(any());
	}

	@Test
	void when_manyThenOneDelayButShorterThanTTL_then_AllEventsAreConsumedOneGetsFinalOne() throws InterruptedException {
		var ttl = 500L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.PERMIT, Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY)
				.verifyComplete();
		Thread.sleep(200L); // shorter than event delay and ttl

		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.DENY)
				.verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(1)).accept(any());
		verify(doFinally, times(1)).accept(any());
	}

	@Test
	void when_manyThenOneDelayButLongerThanTTL_then_AllEventsAreConsumedOneGetsFirstOneAgainAsItIsNewSubscription()
			throws InterruptedException {
		var ttl = 500L;
		var tap = new FluxOneAndManyTap<AuthorizationDecision>(source, Duration.ofMillis(ttl));

		StepVerifier.create(tap.many().map(AuthorizationDecision::getDecision))
				.expectNext(Decision.PERMIT, Decision.INDETERMINATE, Decision.NOT_APPLICABLE, Decision.DENY)
				.verifyComplete();
		Thread.sleep(750L); // longer than ttl

		StepVerifier.create(tap.one().map(AuthorizationDecision::getDecision)).expectNext(Decision.PERMIT)
				.verifyComplete();

		Thread.sleep(ttl + 200L); // Wait for the cache to time out

		verify(onSubscribe, times(2)).accept(any());
		verify(doFinally, times(2)).accept(any());
	}

	@Test
	void when_delayedManySubscription_then_onlyOneIsBufferedAndReplayed() throws InterruptedException {
		var ttl    = 2000L;
		var source = Flux.just(1, 2, 3, 4, 5, 6, 8, 9)
				.concatWith(Flux.just(100, 101, 102).delayElements(Duration.ofMillis(500L)));
		var tap    = new FluxOneAndManyTap<Integer>(source, Duration.ofMillis(ttl));

		tap.one().block();
		Thread.sleep(200L);
		StepVerifier.create(tap.many()).expectNext(9, 100, 101, 102).verifyComplete();
	}

}
