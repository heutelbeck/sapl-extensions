/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.interpreter.pip;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Async;
import org.web3j.utils.Convert;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.pip.contracts.Authorization;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Testing the EthereumPIP with an ethereum blockchain node running in a Docker
 * container. The node is the hyperledger besu implemenation
 * (https://besu.hyperledger.org/en/stable/)
 *
 * This test requires running Docker with version at least 1.6.0 and Docker
 * environment should have more than 2GB free disk space.
 *
 * The accounts used for testing are publicly known from the besu website
 * (https://besu.hyperledger.org/en/stable/Reference/Accounts-for-Testing/) <br>
 * DO NOT USE THESE ACCOUNTS IN THE MAIN NET. ANY ETHER SENT TO THESE ACCOUNTS
 * WILL BE LOST.
 *
 * For these tests to be applied automatically, the maven profile
 * integration-tests has to be activated.
 */

@Testcontainers
@DisplayName("Ethereum integration")
class EthereumIntegrationTests {

    private static final String                 TRANSACTION                     = "transaction";
    private static final String                 HTTP_LOCALHOST                  = "http://localhost:";
    private static final String                 WRONG_NAME                      = "wrongName";
    private static final String                 ACCESS                          = "access";
    private static final String                 ETHEREUM                        = "ethereum";
    private static final String                 OUTPUT_PARAMS                   = "outputParams";
    private static final String                 BOOL                            = "bool";
    private static final String                 INPUT_PARAMS                    = "inputParams";
    private static final String                 VALUE                           = "value";
    private static final String                 ADDRESS                         = "address";
    private static final String                 TYPE                            = "type";
    private static final String                 TO_ACCOUNT                      = "toAccount";
    private static final String                 FROM_ACCOUNT                    = "fromAccount";
    private static final String                 CONTRACT_ADDRESS                = "contractAddress";
    private static final String                 TRANSACTION_HASH                = "transactionHash";
    private static final String                 TRANSACTION_VALUE               = "transactionValue";
    private static final String                 FUNCTION_NAME                   = "functionName";
    private static final String                 IS_AUTHORIZED                   = "isAuthorized";
    private static final String                 USER1_ADDRESS                   = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73";
    private static final String                 USER1_PRIVATE_KEY               = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
    private static final String                 USER2_ADDRESS                   = "0x627306090abaB3A6e1400e9345bC60c78a8BEf57";
    private static final String                 USER3_ADDRESS                   = "0xf17f52151EbEF6C7334FAD080c5704D77216b732";
    private static final String                 USER3_PRIVATE_KEY               = "0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f";
    private static final BigInteger             TRANSACTION1_VALUE              = new BigInteger("2000000000000000000");
    private static final String                 DEFAULT_BLOCK_PARAMETER         = "defaultBlockParameter";
    private static final String                 LATEST                          = "latest";
    private static final String                 POSITION                        = "position";
    private static final String                 BLOCK_HASH                      = "blockHash";
    private static final String                 RETURN_FULL_TRANSACTION_OBJECTS = "returnFullTransactionObjects";
    private static final String                 TRANSACTION_INDEX               = "transactionIndex";
    private static final String                 FILTER_ID                       = "filterId";
    private static final String                 UNCLE_INDEX                     = "uncleIndex";
    private static final AttributeAccessContext EMPTY_CTX                       = new AttributeAccessContext(
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static final JsonMapper               mapper = JsonMapper.builder().build();
    private static Web3j                          web3j;
    private static EthereumPolicyInformationPoint ethPip;
    private static PolicyDecisionPoint            pdp;
    private static final JsonNodeFactory          JSON   = JsonNodeFactory.instance;
    private static String                         authContractAddress;
    private static TransactionReceipt             transactionReceiptUser2;
    private static TransactionReceipt             transactionReceiptUser3;

    @Container
    @SuppressWarnings("resource") // Fine for tests which are short-lived
    static final GenericContainer<? extends GenericContainer<?>> besuContainer = new GenericContainer<>(
            "hyperledger/besu:latest").withExposedPorts(8545, 8546)
            .withCommand("--miner-enabled", "--miner-coinbase=" + USER1_ADDRESS, "--rpc-http-enabled", "--network=dev")
            .waitingFor(Wait.forHttp("/liveness").forStatusCode(200).forPort(8545));

    @BeforeAll
    static void onlyOnce() throws Exception {
        final Integer port = besuContainer.getMappedPort(8545);
        web3j  = Web3j.build(new HttpService(HTTP_LOCALHOST + port), 500, Async.defaultExecutorService());
        ethPip = new EthereumPolicyInformationPoint(web3j);

        String path         = "src/test/resources";
        File   file         = new File(path);
        String absolutePath = file.getAbsolutePath();
        pdp = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of(absolutePath + "/policies"))
                .withPolicyInformationPoint(ethPip).build().pdp();

        Credentials credentials = Credentials.create(USER1_PRIVATE_KEY);
        transactionReceiptUser2 = Transfer.sendFunds(web3j, credentials, USER2_ADDRESS,
                BigDecimal.valueOf(TRANSACTION1_VALUE.doubleValue()), Convert.Unit.WEI).send();
        transactionReceiptUser3 = Transfer
                .sendFunds(web3j, credentials, USER3_ADDRESS, BigDecimal.valueOf(3.3), Convert.Unit.ETHER).send();

        Authorization authContract = Authorization.deploy(web3j, credentials, new DefaultGasProvider()).send();
        authContractAddress = authContract.getContractAddress();
        authContract.authorize(USER2_ADDRESS).send();

    }

    @Test
    void loadContractInformationShouldWorkInAuthorizationPolicy() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(CONTRACT_ADDRESS, authContractAddress);
        saplObject.put(FUNCTION_NAME, IS_AUTHORIZED);
        ArrayNode  inputParams = JSON.arrayNode();
        ObjectNode input1      = JSON.objectNode();
        input1.put(TYPE, ADDRESS);
        input1.put(VALUE, USER2_ADDRESS.substring(2));
        inputParams.add(input1);
        saplObject.set(INPUT_PARAMS, inputParams);
        ArrayNode outputParams = JSON.arrayNode();
        outputParams.add(BOOL);
        saplObject.set(OUTPUT_PARAMS, outputParams);
        AuthorizationSubscription         authzSubscription = AuthorizationSubscription.of(saplObject,
                JSON.stringNode(ACCESS), JSON.stringNode(ETHEREUM), null);
        final Flux<AuthorizationDecision> decision          = pdp.decide(authzSubscription);
        StepVerifier.create(decision).expectNextMatches(authzDecision -> authzDecision.decision() == Decision.PERMIT)
                .thenCancel().verify();
    }

    @Test
    void loadContractInformationWithAuthorizationShouldReturnCorrectValue() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(CONTRACT_ADDRESS, authContractAddress);
        saplObject.put(FUNCTION_NAME, IS_AUTHORIZED);
        ArrayNode  inputParams = JSON.arrayNode();
        ObjectNode input1      = JSON.objectNode();
        input1.put(TYPE, ADDRESS);
        input1.put(VALUE, USER2_ADDRESS.substring(2));
        inputParams.add(input1);
        saplObject.set(INPUT_PARAMS, inputParams);
        ArrayNode outputParams = JSON.arrayNode();
        outputParams.add(BOOL);
        saplObject.set(OUTPUT_PARAMS, outputParams);
        JsonNode result = ValueJsonMarshaller.toJsonNode(
                ethPip.loadContractInformation(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst());

        assertThat(result.get(0).get(VALUE).asBoolean())
                .withFailMessage("False was returned although user2 was authorized and result should have been true.")
                .isTrue();

    }

    @Test
    void verifyTransactionShouldReturnTrueWithCorrectTransaction() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, TRANSACTION1_VALUE);
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as true although it is correct.").isTrue();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithFalseValue() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("25"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the value was false.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithFalseSender() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER3_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the sender was false.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithFalseRecipient() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER3_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the recipient was false.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithFalseTransactionHash() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser3.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result)
                .withFailMessage("Transaction was not validated as false although the TransactionHash was false.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithNullInput() {
        boolean result = ValueJsonMarshaller.toJsonNode(ethPip.verifyTransaction(null, EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the input was null.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithWrongInput1() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(WRONG_NAME, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the input was erroneous.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithWrongInput2() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(WRONG_NAME, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the input was erroneous.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithWrongInput3() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(WRONG_NAME, USER2_ADDRESS);
        saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the input was erroneous.")
                .isFalse();

    }

    @Test
    void verifyTransactionShouldReturnFalseWithWrongInput4() {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
        saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
        saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
        saplObject.put(WRONG_NAME, new BigInteger("2000000000000000000"));
        boolean result = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.verifyTransaction(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .asBoolean();
        assertThat(result).withFailMessage("Transaction was not validated as false although the input was erroneous.")
                .isFalse();

    }

    @Test
    void web3ClientVersionShouldReturnTheClientVersion() throws IOException {
        String pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.web3ClientVersion(null, EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j.web3ClientVersion().send().getWeb3ClientVersion();
        assertThat(pipResult).withFailMessage("The web3ClientVersion from the PIP was not loaded correctly.")
                .isEqualTo(web3jResult);
    }

    @Test
    void web3Sha3ShouldReturnCorrectValuer() throws IOException {
        JsonNode saplObject  = JSON.stringNode(USER3_PRIVATE_KEY);
        String   pipResult   = ValueJsonMarshaller
                .toJsonNode(ethPip.web3Sha3(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .stringValue();
        String   web3jResult = web3j.web3Sha3(USER3_PRIVATE_KEY).send().getResult();
        assertThat(pipResult).withFailMessage("The web3Sha3 method did not work correctly.").isEqualTo(web3jResult);
    }

    @Test
    void netVersionShouldReturnCorrectValue() throws IOException {
        String pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.netVersion(null, EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j.netVersion().send().getNetVersion();
        assertThat(pipResult).withFailMessage("The netVersion method did not work correctly.").isEqualTo(web3jResult);

    }

    @Test
    void netListeningShouldReturnTrueWhenListeningToNetworkConnections() {
        assertThat(ValueJsonMarshaller.toJsonNode(ethPip.netListening(null, EMPTY_CTX).blockFirst()).asBoolean())
                .withFailMessage(
                        "The netListening method did not return true although the Client by default is listening.")
                .isTrue();
    }

    @Test
    void netPeerCountShouldReturnTheCorrectNumber() throws IOException {
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.netPeerCount(null, EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.netPeerCount().send().getQuantity();
        assertThat(pipResult).withFailMessage("The netPeerCount method did not return the correct number.")
                .isEqualTo(web3jResult);
    }

    @Test
    void protocolVersionShouldReturnTheCorrectValue() throws IOException {
        String pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.ethProtocolVersion(null, EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j.ethProtocolVersion().send().getProtocolVersion();
        assertThat(pipResult).withFailMessage("The ethProtocolVersion method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethSyncingShouldReturnTheCorrectValue() throws IOException {
        boolean pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.ethSyncing(null, EMPTY_CTX).blockFirst())
                .asBoolean();
        boolean web3jResult = web3j.ethSyncing().send().getResult().isSyncing();
        assertThat(pipResult).withFailMessage("The ethSyncing method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethCoinbaseShouldReturnTheCorrectValue() throws IOException {
        String pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.ethCoinbase(null, EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j.ethCoinbase().send().getResult();
        assertThat(pipResult).withFailMessage("The ethCoinbase method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethMiningShouldReturnTheCorrectValue() throws IOException {
        boolean pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.ethMining(null, EMPTY_CTX).blockFirst())
                .asBoolean();
        boolean web3jResult = web3j.ethMining().send().getResult();
        assertThat(pipResult).withFailMessage("The ethMining method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethHashrateShouldReturnTheCorrectValue() {
        BigInteger pipResult = ValueJsonMarshaller.toJsonNode(ethPip.ethHashrate(null, EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        assertThat(pipResult.intValue()).withFailMessage("The ethHashrate should be greater than 0.").isGreaterThan(0);
    }

    @Test
    void ethGasPriceShouldReturnTheCorrectValue() throws IOException {
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.ethGasPrice(null, EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGasPrice().send().getGasPrice();
        assertThat(pipResult).withFailMessage("The ethGasPrice method did not return the correct number.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethAccountsShouldReturnTheCorrectValue() throws IOException {

        var value = ethPip.ethAccounts(null, EMPTY_CTX).blockFirst();
        assertThat(value).isInstanceOf(ArrayValue.class);
        List<String> pipResult   = ((ArrayValue) value).stream().map(Value::toString).toList();
        List<String> web3jResult = web3j.ethAccounts().send().getAccounts();
        assertThat(pipResult).withFailMessage("The accounts method did not return the correct accounts.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethBlockNumberShouldReturnTheCorrectValue() throws IOException {
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(ethPip.ethBlockNumber(null, EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethBlockNumber().send().getBlockNumber();
        assertThat(pipResult).withFailMessage("The ethBlockNumber method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetBalanceShouldReturnTheCorrectValue() throws IOException {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(ADDRESS, USER1_ADDRESS);
        saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
        BigInteger pipResult   = ValueJsonMarshaller
                .toJsonNode(ethPip.ethGetBalance(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGetBalance(USER1_ADDRESS, DefaultBlockParameter.valueOf(LATEST)).send()
                .getBalance();
        assertThat(pipResult).withFailMessage("The ethGetBalance method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetStorageAtShouldReturnTheCorrectValue() throws IOException {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(ADDRESS, authContractAddress);
        saplObject.put(POSITION, BigInteger.ZERO);
        saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
        String pipResult   = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.ethGetStorageAt(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j
                .ethGetStorageAt(authContractAddress, BigInteger.ZERO, DefaultBlockParameter.valueOf(LATEST)).send()
                .getData();
        assertThat(pipResult).withFailMessage("The ethGetStorageAt method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetTransactionCountShouldReturnTheCorrectValue() throws IOException {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(ADDRESS, USER1_ADDRESS);
        saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetTransactionCount(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGetTransactionCount(USER1_ADDRESS, DefaultBlockParameter.valueOf(LATEST))
                .send().getTransactionCount();
        assertThat(pipResult).withFailMessage("The ethGetTransactionCount method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetBlockTransactionCountByHashShouldReturnTheCorrectValue() throws IOException {
        String     blockhash  = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(LATEST), false).send()
                .getBlock().getHash();
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(BLOCK_HASH, blockhash);
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetBlockTransactionCountByHash(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX)
                        .blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGetBlockTransactionCountByHash(blockhash).send().getTransactionCount();
        assertThat(pipResult)
                .withFailMessage("The ethGetBlockTransactionCountByHash method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetBlockTransactionCountByNumberShouldReturnTheCorrectValue() throws IOException {
        BigInteger blocknumber = web3j.ethBlockNumber().send().getBlockNumber();
        ObjectNode saplObject  = JSON.objectNode();
        saplObject.put(DEFAULT_BLOCK_PARAMETER, blocknumber);
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetBlockTransactionCountByNumber(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX)
                        .blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGetBlockTransactionCountByNumber(DefaultBlockParameter.valueOf(blocknumber))
                .send().getTransactionCount();
        assertThat(pipResult)
                .withFailMessage("The ethGetBlockTransactionCountByNumber method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetUncleCountByBlockHashShouldReturnTheCorrectValue() throws IOException {
        String     blockhash  = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(LATEST), false).send()
                .getBlock().getHash();
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(BLOCK_HASH, blockhash);
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(ethPip
                .ethGetUncleCountByBlockHash(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGetUncleCountByBlockHash(blockhash).send().getUncleCount();
        assertThat(pipResult)
                .withFailMessage("The ethGetUncleCountByBlockHash method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void uncleCountByBlockNumberShouldReturnTheCorrectValue() throws IOException {
        BigInteger blocknumber = web3j.ethBlockNumber().send().getBlockNumber();
        ObjectNode saplObject  = JSON.objectNode();
        saplObject.put(DEFAULT_BLOCK_PARAMETER, blocknumber);
        BigInteger pipResult   = ValueJsonMarshaller.toJsonNode(ethPip
                .ethGetUncleCountByBlockNumber(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethGetBlockTransactionCountByNumber(DefaultBlockParameter.valueOf(blocknumber))
                .send().getTransactionCount();
        assertThat(pipResult)
                .withFailMessage("The ethGetUncleCountByBlockNumber method did not return the correct value.")
                .isEqualTo(web3jResult);

    }

    @Test
    void ethGetCodeShouldReturnTheCorrectValue() throws IOException {
        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(ADDRESS, authContractAddress);
        String pipResult   = ValueJsonMarshaller
                .toJsonNode(ethPip.ethGetCode(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j.ethGetCode(authContractAddress, DefaultBlockParameter.valueOf(LATEST)).send()
                .getCode();
        assertThat(pipResult).withFailMessage("The ethGetCode method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethCallShouldReturnTheCorrectValue() throws IOException, ClassNotFoundException {
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(TypeReference.makeTypeReference(BOOL));
        List<Type<?>> inputList = new ArrayList<>();
        inputList.add(new Address(USER2_ADDRESS.substring(2)));
        Function    function        = new Function(IS_AUTHORIZED, new ArrayList<>(inputList), outputParameters);
        String      encodedFunction = FunctionEncoder.encode(function);
        Transaction transaction     = Transaction.createEthCallTransaction(USER1_ADDRESS, authContractAddress,
                encodedFunction);

        ObjectNode saplObject = JSON.objectNode();
        saplObject.set(TRANSACTION, mapper.convertValue(transaction, JsonNode.class));

        String pipResult   = ValueJsonMarshaller
                .toJsonNode(ethPip.ethCall(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .stringValue();
        String web3jResult = web3j.ethCall(transaction, DefaultBlockParameter.valueOf(LATEST)).send().getValue();
        assertThat(pipResult).withFailMessage("The ethCall method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethEstimateGasCodeShouldReturnTheCorrectValue() throws IOException, ClassNotFoundException {
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(TypeReference.makeTypeReference(BOOL));
        List<Type<?>> inputList = new ArrayList<>();
        inputList.add(new Address(USER2_ADDRESS.substring(2)));
        Function    function        = new Function(IS_AUTHORIZED, new ArrayList<>(inputList), outputParameters);
        String      encodedFunction = FunctionEncoder.encode(function);
        Transaction transaction     = Transaction.createEthCallTransaction(USER1_ADDRESS, authContractAddress,
                encodedFunction);

        ObjectNode saplObject = JSON.objectNode();
        saplObject.set(TRANSACTION, mapper.convertValue(transaction, JsonNode.class));

        BigInteger pipResult   = ValueJsonMarshaller
                .toJsonNode(ethPip.ethEstimateGas(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .bigIntegerValue();
        BigInteger web3jResult = web3j.ethEstimateGas(transaction).send().getAmountUsed();
        assertThat(pipResult).withFailMessage("The ethEstimateGas method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetBlockByHashShouldReturnTheCorrectValue() throws IOException {
        String blockHash = web3j.ethBlockHashFlowable().blockingFirst();

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(BLOCK_HASH, blockHash);
        saplObject.put(RETURN_FULL_TRANSACTION_OBJECTS, false);
        String pipResult   = ValueJsonMarshaller
                .toJsonNode(
                        ethPip.ethGetBlockByHash(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String web3jResult = mapper
                .convertValue(web3j.ethGetBlockByHash(blockHash, false).send().getBlock(), JsonNode.class).toString();
        assertThat(pipResult).withFailMessage("The ethGetBlockByHash method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetBlockByNumberShouldReturnTheCorrectValue() throws IOException {
        BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
        saplObject.put(RETURN_FULL_TRANSACTION_OBJECTS, false);
        String pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetBlockByNumber(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String web3jResult = mapper.convertValue(
                web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send().getBlock(),
                JsonNode.class).toString();

        assertThat(pipResult).withFailMessage("The ethGetBlockByNumber method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetTransactionByHashShouldReturnTheCorrectValue() throws IOException {

        String   transactionHash = transactionReceiptUser2.getTransactionHash();
        JsonNode saplObject      = JSON.stringNode(transactionHash);
        String   pipResult       = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetTransactionByHash(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String   web3jResult     = mapper
                .convertValue(web3j.ethGetTransactionByHash(transactionHash).send().getResult(), JsonNode.class)
                .toString();

        assertThat(pipResult).withFailMessage("The ethGetTransactionByHash method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetTransactionByBlockHashAndIndexShouldReturnTheCorrectValue() throws IOException {

        String     blockHash = transactionReceiptUser2.getBlockHash();
        BigInteger index     = transactionReceiptUser2.getTransactionIndex();

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(BLOCK_HASH, blockHash);
        saplObject.put(TRANSACTION_INDEX, index);

        String pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetTransactionByBlockHashAndIndex(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX)
                        .blockFirst())
                .toString();
        String web3jResult = mapper
                .convertValue(web3j.ethGetTransactionByBlockHashAndIndex(blockHash, index).send().getResult(),
                        JsonNode.class)
                .toString();

        assertThat(pipResult)
                .withFailMessage("The ethGetTransactionByBlockHashAndIndex method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetTransactionByBlockNumberAndIndexShouldReturnTheCorrectValue() throws IOException {

        BigInteger blockNumber = transactionReceiptUser2.getBlockNumber();
        BigInteger index       = transactionReceiptUser2.getTransactionIndex();

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
        saplObject.put(TRANSACTION_INDEX, index);

        String pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetTransactionByBlockNumberAndIndex(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX)
                        .blockFirst())
                .toString();
        String web3jResult = mapper.convertValue(
                web3j.ethGetTransactionByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), index).send()
                        .getResult(),
                JsonNode.class).toString();

        assertThat(pipResult)
                .withFailMessage("The ethGetTransactionByBlockNumberAndIndex method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetTransactionReceiptShouldReturnTheCorrectValue() throws IOException {

        String   transactionHash = transactionReceiptUser2.getTransactionHash();
        JsonNode saplObject      = JSON.stringNode(transactionHash);

        String pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetTransactionReceipt(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String web3jResult = mapper
                .convertValue(web3j.ethGetTransactionReceipt(transactionHash).send().getResult(), JsonNode.class)
                .toString();

        assertThat(pipResult).withFailMessage("The ethGetTransactionReceipt method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetUncleByBlockHashAndIndexShouldThrowAttributeExceptionWithNoUncle() throws IOException {

        String     blockHash = transactionReceiptUser2.getBlockHash();
        BigInteger index     = BigInteger.ZERO;

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(BLOCK_HASH, blockHash);
        saplObject.put(UNCLE_INDEX, index);

        String pipResult   = ValueJsonMarshaller.toJsonNode(ethPip
                .ethGetUncleByBlockHashAndIndex(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String web3jResult = mapper
                .convertValue(web3j.ethGetUncleByBlockHashAndIndex(blockHash, index).send().getResult(), JsonNode.class)
                .toString();

        assertThat(pipResult)
                .withFailMessage("The ethGetUncleByBlockHashAndIndex method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetUncleByBlockNumberAndIndexShouldThrowAttributeExceptionWithNoUncle() throws IOException {

        BigInteger blockNumber = transactionReceiptUser2.getBlockNumber();
        BigInteger index       = BigInteger.ZERO;

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
        saplObject.put(UNCLE_INDEX, index);

        String pipResult   = ValueJsonMarshaller.toJsonNode(ethPip
                .ethGetUncleByBlockNumberAndIndex(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String web3jResult = mapper
                .convertValue(web3j.ethGetUncleByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), index)
                        .send().getResult(), JsonNode.class)
                .toString();

        assertThat(pipResult)
                .withFailMessage("The ethGetUncleByBlockNumberAndIndex method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

    @Test
    void ethGetFilterChangesShouldReturnTheCorrectValue() throws IOException {

        BigInteger filterId = web3j.ethNewBlockFilter().send().getFilterId();

        ObjectNode saplObject = JSON.objectNode();
        saplObject.put(FILTER_ID, filterId);

        String pipResult   = ValueJsonMarshaller.toJsonNode(
                ethPip.ethGetFilterChanges(ValueJsonMarshaller.fromJsonNode(saplObject), EMPTY_CTX).blockFirst())
                .toString();
        String web3jResult = web3j.ethGetFilterChanges(filterId).send().getResult().toString();

        assertThat(pipResult).withFailMessage("The ethGetFilterChanges method did not return the correct value.")
                .isEqualTo(web3jResult);
    }

}
