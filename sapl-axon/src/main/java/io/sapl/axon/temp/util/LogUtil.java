package io.sapl.axon.temp.util;

import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class LogUtil {

	public static void dumpSubscriptionMessage(String intro, SubscriptionQueryMessage<?,?,?> query) {
		log.info(intro+":");
		log.info("toString    : {}", query);
		log.info("class       : {}", query.getClass().getSimpleName());
		log.info("name        : {}", query.getQueryName());
		log.info("metadata    : {}", query.getMetaData());
		log.info("respose type: {}", query.getResponseType());
		log.info("update type : {}", query.getUpdateResponseType());
		log.info("update type*: {}", query.getUpdateResponseType().getExpectedResponseType());
		log.info("payload type: {}", query.getPayloadType());
		log.info("payload     : {}", query.getPayload());
	}

	public static void dumpQueryMessage(String intro, QueryMessage<?,?> query) {
		log.info(intro+":");
		log.info("toString    : {}", query);
		log.info("class       : {}", query.getClass().getSimpleName());
		log.info("name        : {}", query.getQueryName());
		log.info("metadata    : {}", query.getMetaData());
		log.info("respose type: {}", query.getResponseType());
		log.info("payload type: {}", query.getPayloadType());
		log.info("payload     : {}", query.getPayload());
	}
}
