/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.interpreter.pip.EthereumBasicFunctions.getBigIntFrom;
import static io.sapl.interpreter.pip.EthereumBasicFunctions.getBooleanFrom;
import static io.sapl.interpreter.pip.EthereumBasicFunctions.getJsonFrom;
import static io.sapl.interpreter.pip.EthereumBasicFunctions.getStringFrom;
import static io.sapl.interpreter.pip.EthereumBasicFunctions.toVal;
import static io.sapl.interpreter.pip.EthereumPipFunctions.createEncodedFunction;
import static io.sapl.interpreter.pip.EthereumPipFunctions.createFunction;
import static io.sapl.interpreter.pip.EthereumPipFunctions.getDefaultBlockParameter;
import static io.sapl.interpreter.pip.EthereumPipFunctions.getEthFilterFrom;
import static io.sapl.interpreter.pip.EthereumPipFunctions.getTransactionFromJson;
import static org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Ethereum Policy Information Point gives access to most methods of the
 * <a href="https://github.com/ethereum/wiki/wiki/JSON-RPC">JSON-RPC Ethereum
 * API</a>.
 * <p>
 * Excluded are all methods that would change the state of the blockchain as it
 * doesn't make sense to use them during a policy evaluation. These methods are
 * eth_sendTransaction, eth_sendRawTransaction, eth_submitWork and
 * eth_submitHashrate. The methods that are changing something in the node are
 * excluded, because creating or managing filters and shh identities should not
 * be done inside a policy. These methods are eth_newFilter, eth_newBlockFilter,
 * eth_newPendingTransactionFilter, eth_uninstallFilter, shh_post,
 * shh_newIdentity, shh_addToGroup, shh_newFilter and shh_uninstallFilter. Also
 * excluded are the deprecated methods eth_getCompilers, eth_compileSolidity,
 * eth_compileLLL and eth_compileSerpent. Further, excluded are all db_ methods
 * as they are deprecated and will be removed. Also excluded is the eth_getProof
 * method as at time of writing this there doesn't exist an implementation in
 * the Web3j API.
 * <p>
 * Finally, the methods verifyTransaction and loadContractInformation are not
 * part of the JSON RPC API but are considered to be a more user-friendly
 * implementation of the most common use cases.
 */

@Slf4j
@PolicyInformationPoint(name = "ethereum", description = "Connects to the Ethereum Blockchain.")
public class EthereumPolicyInformationPoint {

    private static final String       ETH_PIP_CONFIG                  = "ethPipConfig";
    private static final long         DEFAULT_ETH_POLLING_INTERVAL    = 5000L;
    private static final String       ADDRESS                         = "address";
    private static final String       CONTRACT_ADDRESS                = "contractAddress";
    private static final String       TRANSACTION_HASH                = "transactionHash";
    private static final String       FROM_ACCOUNT                    = "fromAccount";
    private static final String       TO_ACCOUNT                      = "toAccount";
    private static final String       TRANSACTION_VALUE               = "transactionValue";
    private static final String       INPUT_PARAMS                    = "inputParams";
    private static final String       OUTPUT_PARAMS                   = "outputParams";
    private static final String       FUNCTION_NAME                   = "functionName";
    private static final String       POSITION                        = "position";
    private static final String       BLOCK_HASH                      = "blockHash";
    private static final String       SHA3_HASH_OF_DATA_TO_SIGN       = "sha3HashOfDataToSign";
    private static final String       TRANSACTION                     = "transaction";
    private static final String       RETURN_FULL_TRANSACTION_OBJECTS = "returnFullTransactionObjects";
    private static final String       TRANSACTION_INDEX               = "transactionIndex";
    private static final String       UNCLE_INDEX                     = "uncleIndex";
    private static final String       FILTER_ID                       = "filterId";
    private static final String       DEFAULT_BLOCK_PARAMETER         = "defaultBlockParameter";
    private static final String       VERIFY_TRANSACTION_WARNING      = "There was an error during verifyTransaction. By default false is returned but the transaction could have taken place.";
    private static final ObjectMapper MAPPER                          = new ObjectMapper();
    private static final String       ETH_POLLING_INTERVAL            = "ethPollingInterval";
    private final Web3j               web3j;

    public EthereumPolicyInformationPoint() {
        this(Web3j.build(new HttpService()));
    }

    public EthereumPolicyInformationPoint(Web3j web3j) {
        this.web3j = web3j;
    }

    /**
     * Method for verifying if a given transaction has taken place.
     *
     * @param leftHandValue needs to have the following values: <br>
     *                      "transactionHash" : The hash of the transaction that
     *                      should be verified <br>
     *                      "fromAccount" : The address of the account the
     *                      transaction is sent from <br>
     *                      "toAccount" : The address of the account that receives
     *                      the transaction <br>
     *                      "transactionValue" : A BigInteger that represents the
     *                      value of the transaction in Wei
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes that have boolean value true if the transaction
     *         has taken place and false otherwise @
     */
    @Attribute(name = "transaction", docs = "Returns true, if a transaction has taken place and false otherwise.")
    public Flux<Val> verifyTransaction(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withVerifiedTransaction(leftHandValue), variables);
    }

    private Callable<Val> withVerifiedTransaction(Val saplObject) {
        return () -> {
            try {
                var object = saplObject.get();
                web3j.ethAccounts().flowable();
                Optional<Transaction> optionalTransaction = web3j
                        .ethGetTransactionByHash(getStringFrom(object, TRANSACTION_HASH)).send().getTransaction();
                if (optionalTransaction.isPresent()) {
                    Transaction transaction = optionalTransaction.get();
                    if (transaction.getFrom().equalsIgnoreCase(getStringFrom(object, FROM_ACCOUNT))
                            && transaction.getTo().equalsIgnoreCase(getStringFrom(object, TO_ACCOUNT))
                            && transaction.getValue().equals(getBigIntFrom(object, TRANSACTION_VALUE))) {
                        return Val.TRUE;
                    }
                }
            } catch (IOException | NullPointerException | ClientConnectionException e) {
                log.warn(VERIFY_TRANSACTION_WARNING);
            }
            return Val.FALSE;
        };
    }

    /**
     * Method for querying the state of a contract.
     *
     * @param leftHandValue needs to have the following values <br>
     *                      "fromAccount" : (Optional) The account that makes the
     *                      request <br>
     *                      "contractAddress" : The address of the called contract
     *                      <br>
     *                      "functionName" : The name of the called function. <br>
     *                      "inputParams" : A Json ArrayNode that contains a tuple
     *                      of "type" and "value" for each input parameter. Example:
     *                      [{"type" : "uint32", "value" : 45},{"type" : "bool",
     *                      "value" : "true"}] <br>
     *                      "outputParams" : A Json ArrayNode that contains the
     *                      return types. Example: ["address","bool"] <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending". <br>
     *                      <br>
     *                      All types that can be used are listed in the
     *                      convertToType-method of the <a href=
     *                      "https://github.com/heutelbeck/sapl-policy-engine/blob/sapl-ethereum/sapl-ethereum/src/main/java/io/sapl/interpreter/pip/EthereumPipFunctions.java">EthereumPipFunctions</a>.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of ArrayNodes that contain the return value(s) of the called
     *         contract function. Each node entry contains two values, "value" with
     *         the return value and "typeAsString" with the return type. Example for
     *         a return array: [{"value":true,"typeAsString":"bool"},
     *         {"value":324,"typeAsString":"uint"}] @
     */
    @Attribute(name = "contract", docs = "Returns the result of a function call of a specified contract.")
    public Flux<Val> loadContractInformation(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withInformationFromContract(leftHandValue), variables);
    }

    private Callable<Val> withInformationFromContract(Val value) {
        JsonNode saplObject = value.get();
        return () -> {
            String   fromAccount     = getStringFrom(saplObject, FROM_ACCOUNT);
            String   contractAddress = getStringFrom(saplObject, CONTRACT_ADDRESS);
            String   functionName    = getStringFrom(saplObject, FUNCTION_NAME);
            JsonNode inputParams     = getJsonFrom(saplObject, INPUT_PARAMS);
            JsonNode outputParams    = getJsonFrom(saplObject, OUTPUT_PARAMS);
            JsonNode dbp             = getJsonFrom(saplObject, DEFAULT_BLOCK_PARAMETER);

            Function function        = createFunction(functionName, inputParams, outputParams);
            String   encodedFunction = createEncodedFunction(function);

            String response = web3j.ethCall(createEthCallTransaction(fromAccount, contractAddress, encodedFunction),
                    getDefaultBlockParameter(dbp)).send().getValue();

            return toVal(FunctionReturnDecoder.decode(response, function.getOutputParameters()));
        };
    }

    /**
     * This simply returns the version of the client running the node that the
     * EthPip connects to.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes containing a string with the clientVersion
     */
    @Attribute(name = "clientVersion", docs = "Returns the current client version.")
    public Flux<Val> web3ClientVersion(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withWeb3ClientVersion(), variables);
    }

    private Callable<Val> withWeb3ClientVersion() {
        return () -> toVal(web3j.web3ClientVersion().send().getWeb3ClientVersion());
    }

    /**
     * This function can be used to get the Keccak-256 Hash (which is commonly used
     * in Ethereum) of a given hex value.
     *
     * @param leftHandValue should contain only a string that has to be a hex value,
     *                      otherwise the hash can't be calculated.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes containing a string with the hash value of the
     *         data.
     */
    @Attribute(name = "sha3", docs = "Returns Keccak-256 (not the standardized SHA3-256) of the given data.")
    public Flux<Val> web3Sha3(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withWeb3Sha3(leftHandValue), variables);
    }

    private Callable<Val> withWeb3Sha3(Val saplObject) {
        return () -> toVal(web3j.web3Sha3(saplObject.get().textValue()).send().getResult());
    }

    /**
     * Method for querying the id of the network the client is connected to. Common
     * network ids are 1 for the Ethereum Mainnet, 3 for Ropsten Tesnet, 4 for
     * Rinkeby testnet and 42 for Kovan Testnet. Any other id most probably refers
     * to a private testnet.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes containing a string with the current network id.
     */
    @Attribute(name = "netVersion", docs = "Returns the current network id.")
    public Flux<Val> netVersion(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withNetVersion(), variables);
    }

    private Callable<Val> withNetVersion() {
        return () -> toVal(web3j.netVersion().send().getNetVersion());
    }

    /**
     * A simple method that checks if the client is listening for network
     * connections.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes with boolean value true if listening and false
     *         otherwise.
     */
    @Attribute(name = "listening", docs = "Returns true if client is actively listening for network connections.")
    public Flux<Val> netListening(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withNetListening(), variables);
    }

    private Callable<Val> withNetListening() {
        return () -> toVal(web3j.netListening().send().isListening());
    }

    /**
     * Method to find out the number of connected peers.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes with the number of connected peers as BigInteger
     *         value.
     */
    @Attribute(name = "peerCount", docs = "Returns number of peers currently connected to the client.")
    public Flux<Val> netPeerCount(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withNetPeerCount(), variables);
    }

    private Callable<Val> withNetPeerCount() {
        return () -> toVal(web3j.netPeerCount().send().getQuantity());
    }

    /**
     * Method for querying the version of the currently used ethereum protocol.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes that contain the protocol version as a String
     */
    @Attribute(name = "protocolVersion", docs = "Returns the current ethereum protocol version.")
    public Flux<Val> ethProtocolVersion(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthProtocolVersion(), variables);
    }

    private Callable<Val> withEthProtocolVersion() {
        return () -> toVal(web3j.ethProtocolVersion().send().getProtocolVersion());
    }

    /**
     * Simple method to check if the client is currently syncing with the network.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes with boolean value true if syncing and false
     *         otherwise.
     */
    @Attribute(name = "syncing", docs = "Returns true if the client is syncing or false otherwise.")
    public Flux<Val> ethSyncing(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthSyncing(), variables);
    }

    private Callable<Val> withEthSyncing() {
        return () -> toVal(web3j.ethSyncing().send().isSyncing());
    }

    /**
     * Method for retrieving the address of the client coinbase.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes containing the address of the client coinbase as a
     *         String.
     */
    @Attribute(name = "coinbase", docs = "Returns the client coinbase address.")
    public Flux<Val> ethCoinbase(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthCoinbase(), variables);

    }

    private Callable<Val> withEthCoinbase() {
        return () -> toVal(web3j.ethCoinbase().send().getResult());
    }

    /**
     * Simple method to check if the client is mining.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes with boolean value true if mining and false
     *         otherwise.
     */
    @Attribute(name = "mining", docs = "Returns true if client is actively mining new blocks.")
    public Flux<Val> ethMining(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthMining(), variables);

    }

    private Callable<Val> withEthMining() {
        return () -> toVal(web3j.ethMining().send().isMining());
    }

    /**
     * Method for querying the number of hashes per second that the client is mining
     * with.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes with the hashrate as BigInteger value.
     */
    @Attribute(name = "hashrate", docs = "Returns the number of hashes per second that the node is mining with.")
    public Flux<Val> ethHashrate(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthHashrate(), variables);
    }

    private Callable<Val> withEthHashrate() {
        return () -> toVal(web3j.ethHashrate().send().getHashrate());
    }

    /**
     * Method for querying the current gas price in wei.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes containing the gas price as BigInteger value.
     */
    @Attribute(name = "gasPrice", docs = "Returns the current price per gas in wei.")
    public Flux<Val> ethGasPrice(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthGasPrice(), variables);
    }

    private Callable<Val> withEthGasPrice() {
        return () -> toVal(web3j.ethGasPrice().send().getGasPrice());
    }

    /**
     * Method for returning all addresses owned by the client.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of ArrayNodes that contain the owned addresses as Strings.
     */
    @Attribute(name = "accounts", docs = "Returns a list of addresses owned by client.")
    public Flux<Val> ethAccounts(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthAccounts(), variables);
    }

    private Callable<Val> withEthAccounts() {
        return () -> toVal(web3j.ethAccounts().send().getAccounts());
    }

    /**
     * Method for receiving the number of the most recent block.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return Flux of JsonNodes containing the block number as a BigInteger.
     */
    @Attribute(name = "blockNumber", docs = "Returns the number of most recent block.")
    public Flux<Val> ethBlockNumber(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEthBlockNumber(), variables);
    }

    private Callable<Val> withEthBlockNumber() {
        return () -> toVal(web3j.ethBlockNumber().send().getBlockNumber());
    }

    /**
     * Method for querying the balance of an account at a given block. If no
     * DefaultBlockParameter is provided the latest Block will be queried.
     *
     * @param leftHandValue needs to have the following values: <br>
     *                      "address": The address of the account that you want to
     *                      get the balance of. <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     SAPL variables
     * @return Flux of JsonNodes holding the balance in wei as BigInteger.
     *
     */
    @Attribute(name = "balance", docs = "Returns the balance of the account of given address.")
    public Flux<Val> ethGetBalance(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withAccountBalance(leftHandValue), variables);

    }

    private Callable<Val> withAccountBalance(Val saplObject) {
        var object = saplObject.get();
        return () -> toVal(web3j.ethGetBalance(getStringFrom(object, ADDRESS), getDefaultBlockParameter(object)).send()
                .getBalance());
    }

    /**
     * Method that returns the value of a storage at a certain position. Refer to
     * the <a href=
     * "https://github.com/ethereum/wiki/wiki/JSON-RPC#eth_getstorageat">Json-RPC</a>
     * to find out how the storage position is being calculated.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "address": Address of the contract that the storage
     *                      belongs to. <br>
     *                      "position": Position of the stored data. <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes that contain the stored value at the denoted
     *         position.
     */
    @Attribute(name = "storage", docs = "Returns the value from a storage position at a given address.")
    public Flux<Val> ethGetStorageAt(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withStorageAt(leftHandValue), variables);

    }

    private Callable<Val> withStorageAt(Val saplObject) {
        var object = saplObject.get();
        return () -> toVal(web3j.ethGetStorageAt(getStringFrom(object, ADDRESS), object.get(POSITION).bigIntegerValue(),
                getDefaultBlockParameter(object)).send().getData());
    }

    /**
     * Method that returns the amount of transactions that an externally owned
     * account has sent or the number of interactions with other contracts in the
     * case of a contract account.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "address": Address of the account that the
     *                      transactionCount should be returned from. <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes that contain the transaction count as a
     *         BigInteger value.
     */
    @Attribute(name = "transactionCount", docs = "Returns the number of transactions sent from an address.")
    public Flux<Val> ethGetTransactionCount(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withTransactionCount(leftHandValue), variables);

    }

    private Callable<Val> withTransactionCount(Val saplObject) {
        var object = saplObject.get();
        return () -> toVal(
                web3j.ethGetTransactionCount(getStringFrom(object, ADDRESS), getDefaultBlockParameter(object)).send()
                        .getTransactionCount());
    }

    /**
     * Method for querying the number of transactions in a block with a given hash.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "blockHash": The hash of the block in question as
     *                      String.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes holding the transaction count of the block as
     *         BigInteger value.
     */
    @Attribute(name = "blockTransactionCountByHash", docs = "Returns the number of transactions in a block from a block matching the given block hash.")
    public Flux<Val> ethGetBlockTransactionCountByHash(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withBlockTransactionCountByHash(leftHandValue), variables);

    }

    private Callable<Val> withBlockTransactionCountByHash(Val saplObject) {
        return () -> toVal(web3j.ethGetBlockTransactionCountByHash(getStringFrom(saplObject.get(), BLOCK_HASH)).send()
                .getTransactionCount());
    }

    /**
     * Method for querying the number of transactions in a block with a given
     * number.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes holding the transaction count of the block as
     *         BigInteger value.
     */
    @Attribute(name = "blockTransactionCountByNumber", docs = "Returns the number of transactions in a block matching the given block number.")
    public Flux<Val> ethGetBlockTransactionCountByNumber(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withBlockTransactionCountByNumber(leftHandValue), variables);
    }

    private Callable<Val> withBlockTransactionCountByNumber(Val saplObject) {
        return () -> toVal(web3j.ethGetBlockTransactionCountByNumber(getDefaultBlockParameter(saplObject.get())).send()
                .getTransactionCount());
    }

    /**
     * Method for querying the number of uncles in a block with a given hash.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "blockHash": The hash of the block in question as
     *                      String.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes holding the uncle count of the block as
     *         BigInteger value.
     */
    @Attribute(name = "uncleCountByBlockHash", docs = "Returns the number of uncles in a block from a block matching the given block hash.")
    public Flux<Val> ethGetUncleCountByBlockHash(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withUncleCountByBlockHash(leftHandValue), variables);
    }

    private Callable<Val> withUncleCountByBlockHash(Val saplObject) {
        return () -> toVal(
                web3j.ethGetUncleCountByBlockHash(getStringFrom(saplObject.get(), BLOCK_HASH)).send().getUncleCount());
    }

    /**
     * Method for querying the number of uncles in a block with a given number.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes holding the uncle count of the block as
     *         BigInteger value.
     */
    @Attribute(name = "uncleCountByBlockNumber", docs = "Returns the number of uncles in a block from a block matching the given block number.")
    public Flux<Val> ethGetUncleCountByBlockNumber(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withUncleCountByBlockNumber(leftHandValue), variables);
    }

    private Callable<Val> withUncleCountByBlockNumber(Val saplObject) {
        return () -> toVal(
                web3j.ethGetUncleCountByBlockNumber(getDefaultBlockParameter(saplObject.get())).send().getUncleCount());
    }

    /**
     * Method for getting the code stored at a certain address.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "address": Address of the contract that the code should
     *                      be returned from. <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes containing the code at the address as String.
     */
    @Attribute(name = "code", docs = "Returns code at a given address.")
    public Flux<Val> ethGetCode(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withCode(leftHandValue), variables);
    }

    private Callable<Val> withCode(Val saplObject) {
        var object = saplObject.get();
        return () -> toVal(
                web3j.ethGetCode(getStringFrom(object, ADDRESS), getDefaultBlockParameter(object)).send().getCode());
    }

    /**
     * Method for calculating the signature needed for Ethereum transactions. The
     * address to sign with mus be unlocked in the client.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "address": Address used to sign with. <br>
     *                      "sha3HashOfDataToSign": The message that should be
     *                      signed.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes holding the resulting signature in form of a
     *         String.
     */
    @Attribute(name = "sign", docs = "The sign method calculates an Ethereum specific signature.")
    public Flux<Val> ethSign(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withSignature(leftHandValue), variables);
    }

    private Callable<Val> withSignature(Val saplObject) {
        var object = saplObject.get();
        return () -> toVal(
                web3j.ethSign(getStringFrom(object, ADDRESS), getStringFrom(object, SHA3_HASH_OF_DATA_TO_SIGN)).send()
                        .getSignature());
    }

    /**
     * This method can be used for querying a contract without creating a
     * transaction. To use it just hand in a transaction converted to JsonNode with
     * the ObjectMapper. An example can be found in the documentation.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "transaction": An
     *                      org.web3j.protocol.core.methods.request.Transaction
     *                      mapped to JsonNode. <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes with the result of the call in form of a String.
     */
    @Attribute(name = "call", docs = "Executes a new message call immediately without creating a transaction on the block chain.")
    public Flux<Val> ethCall(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withCallResult(leftHandValue), variables);
    }

    private Callable<Val> withCallResult(Val saplObject) {
        var object = saplObject.get();
        return () -> toVal(
                web3j.ethCall(getTransactionFromJson(object.get(TRANSACTION)), getDefaultBlockParameter(object)).send()
                        .getValue());
    }

    /**
     * This method can be used to estimate the gas cost of a given transaction. It
     * doesn't cause any transaction to be sent. To use it just hand in a
     * transaction converted to JsonNode with the ObjectMapper. An example can be
     * found in the documentation.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "transaction": An
     *                      org.web3j.protocol.core.methods.request.Transaction
     *                      mapped to JsonNode.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of JsonNodes containing the estimated gas value as BigInteger.
     */
    @Attribute(name = "estimateGas", docs = "Generates and returns an estimate of how much gas is necessary to allow the transaction to complete.")
    public Flux<Val> ethEstimateGas(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withEstimatedGas(leftHandValue), variables);
    }

    private Callable<Val> withEstimatedGas(Val saplObject) {
        return () -> toVal(
                web3j.ethEstimateGas(getTransactionFromJson(saplObject.get().get(TRANSACTION))).send().getAmountUsed());
    }

    /**
     * Method to retrieve a complete block by using its hash.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "blockHash": The hash of the block that should be
     *                      retrieved. <br>
     *                      "returnFullTransactionObjects": (Optional) To include
     *                      the full transaction objects this value has to be true.
     *                      If false or not provided, only the transaction hashes
     *                      will be included.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json nodes containing the returned block mapped to Json.
     */
    @Attribute(name = "blockByHash", docs = "Returns information about a block by hash.")
    public Flux<Val> ethGetBlockByHash(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withBlockByHash(leftHandValue.get()), variables);
    }

    private Callable<Val> withBlockByHash(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetBlockByHash(getStringFrom(saplObject, BLOCK_HASH),
                getBooleanFrom(saplObject, RETURN_FULL_TRANSACTION_OBJECTS)).send().getBlock());
    }

    /**
     * Method to retrieve a complete block by using its number.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "returnFullTransactionObjects": (Optional) To include
     *                      the full transaction objects this value has to be true.
     *                      If false or not provided, only the transaction hashes
     *                      will be included. <br>
     *                      "defaultBlockParameter": (Optional) BigInteger value of
     *                      the desired block number <b>or</b> one of the strings
     *                      "latest", "earliest", or "pending".
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json nodes containing the returned block mapped to Json.
     */
    @Attribute(name = "blockByNumber", docs = "Returns information about a block by block number.")
    public Flux<Val> ethGetBlockByNumber(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withBlockByNumber(leftHandValue.get()), variables);
    }

    private Callable<Val> withBlockByNumber(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetBlockByNumber(getDefaultBlockParameter(saplObject),
                getBooleanFrom(saplObject, RETURN_FULL_TRANSACTION_OBJECTS)).send().getBlock());
    }

    /**
     * Method for getting the full information of a transaction by providing its
     * hash.
     *
     * @param leftHandValue should only be the transaction hash.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes containing the mapped transaction.
     */
    @Attribute(name = "transactionByHash", docs = "Returns the information about a transaction requested by transaction hash.")
    public Flux<Val> ethGetTransactionByHash(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withTransactionByHash(leftHandValue), variables);
    }

    private Callable<Val> withTransactionByHash(Val saplObject) {
        return () -> toVal(web3j.ethGetTransactionByHash(saplObject.get().textValue()).send().getResult());
    }

    /**
     * Method for getting the full information of a transaction by providing the
     * block hash and index to find it.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "blockHash": Hash of the block the transaction is in.
     *                      <br>
     *                      "transactionIndex": Position of the transaction in the
     *                      block. <br>
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes containing the mapped transaction.
     */
    @Attribute(name = "transactionByBlockHashAndIndex", docs = "Returns information about a transaction by block hash and transaction index position.")
    public Flux<Val> ethGetTransactionByBlockHashAndIndex(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withTransactionByBlockHashAndIndex(leftHandValue.get()), variables);
    }

    private Callable<Val> withTransactionByBlockHashAndIndex(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetTransactionByBlockHashAndIndex(getStringFrom(saplObject, BLOCK_HASH),
                getBigIntFrom(saplObject, TRANSACTION_INDEX)).send().getResult());
    }

    /**
     * Method for getting the full information of a transaction by providing the
     * block number and index to find it.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "defaultBlockParameter": Should in this case hold the
     *                      number of the Block as BigInteger. "transactionIndex":
     *                      The position of the transaction in the block.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes containing the mapped transaction.
     */
    @Attribute(name = "transactionByBlockNumberAndIndex", docs = "Returns information about a transaction by block number and transaction index position.")
    public Flux<Val> ethGetTransactionByBlockNumberAndIndex(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withTransactionByBlockNumberAndIndex(leftHandValue.get()), variables);
    }

    private Callable<Val> withTransactionByBlockNumberAndIndex(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetTransactionByBlockNumberAndIndex(getDefaultBlockParameter(saplObject),
                getBigIntFrom(saplObject, TRANSACTION_INDEX)).send().getResult());
    }

    /**
     * Method for getting the transaction receipt by the hash of the corresponding
     * transaction.
     *
     * @param leftHandValue should contain only the transaction hash as a String.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes
     */
    @Attribute(name = "transactionReceipt", docs = "Returns the receipt of a transaction by transaction hash.")
    public Flux<Val> ethGetTransactionReceipt(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withTransactionReceipt(leftHandValue.get()), variables);
    }

    private Callable<Val> withTransactionReceipt(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetTransactionReceipt(saplObject.textValue()).send().getResult());
    }

    /**
     * Method for getting the hashes of pending transactions (transactions that have
     * been broadcast, but not yet mined into a block).
     *
     * @param leftHandValue is unused here
     * @param variables     is unused here
     * @return A Flux of Json Nodes that hold the hashes of the pending
     *         transactions.
     */
    @Attribute(name = "pendingTransactions", docs = "Returns the pending transactions list.")
    public Flux<Val> ethPendingTransactions(Val leftHandValue, Map<String, JsonNode> variables) {
        return Flux.from(web3j.ethPendingTransactionHashFlowable().map(s -> MAPPER.convertValue(s, JsonNode.class))
                .map(Val::of));
    }

    /**
     * Method for getting an uncle by block hash and position of the uncle.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "blockHash": Hash of the block the uncle is in. <br>
     *                      "uncleIndex": Position in the uncles list as BigInteger.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes containing the mapped uncle.
     */
    @Attribute(name = "uncleByBlockHashAndIndex", docs = "Returns information about a uncle of a block by hash and uncle index position.")
    public Flux<Val> ethGetUncleByBlockHashAndIndex(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withUncleByBlockHashAndIndex(leftHandValue.get()), variables);
    }

    private Callable<Val> withUncleByBlockHashAndIndex(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetUncleByBlockHashAndIndex(getStringFrom(saplObject, BLOCK_HASH),
                getBigIntFrom(saplObject, UNCLE_INDEX)).send().getBlock());
    }

    /**
     * Method for getting an uncle by block number and position of the uncle.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "defaultBlockParameter": Here it should hold the number
     *                      of the block the uncle is in. <br>
     *                      "uncleIndex": Position in the uncles list as BigInteger.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes containing the mapped uncle.
     */
    @Attribute(name = "uncleByBlockNumberAndIndex", docs = "Returns information about a uncle of a block by number and uncle index position.")
    public Flux<Val> ethGetUncleByBlockNumberAndIndex(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withUncleByBlockNumberAndIndex(leftHandValue.get()), variables);
    }

    private Callable<Val> withUncleByBlockNumberAndIndex(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetUncleByBlockNumberAndIndex(getDefaultBlockParameter(saplObject),
                getBigIntFrom(saplObject, UNCLE_INDEX)).send().getBlock());
    }

    /**
     * This method returns a list of filter logs that occurred since the last
     * received list.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "filterId": The identification number of the requested
     *                      filter as BigInteger.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes containing arrays of new filter logs.
     */
    @Attribute(name = "ethFilterChanges", docs = "Returns an array of logs which occurred since last poll.")
    public Flux<Val> ethGetFilterChanges(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withFilterChanges(leftHandValue.get()), variables);
    }

    private Callable<Val> withFilterChanges(JsonNode saplObject) {
        return () -> toVal(web3j.ethGetFilterChanges(getBigIntFrom(saplObject, FILTER_ID)).send().getLogs());
    }

    /**
     * Method that returns all logs matching a given filter id.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "filterId": The identification number of the requested
     *                      filter as BigInteger.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes that contain an array of all filter logs from a
     *         given filter.
     */
    @Attribute(name = "ethFilterLogs", docs = "Returns an array of all logs matching filter with given id.")
    public Flux<Val> ethGetFilterLogs(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withFilterLogs(leftHandValue), variables);
    }

    private Callable<Val> withFilterLogs(Val saplObject) {
        return () -> toVal(web3j.ethGetFilterLogs(getBigIntFrom(saplObject.get(), FILTER_ID)).send().getLogs());
    }

    /**
     * Method that returns all logs matching a given filter object.
     *
     * @param leftHandValue needs to hold the following values: <br>
     *                      "fromBlock": Hex value of the block from which on should
     *                      be filtered from (beginning with 0x). <br>
     *                      "toBlock": Hex value of the block from where the
     *                      filtering should end (beginning with 0x). <br>
     *                      "address": An array of addresses that should be reviewed
     *                      by the filter. <br>
     *                      You can simply map an EthFilter object to Json in order
     *                      to get the required values.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes that contain an array of all filter logs from a
     *         given filter object.
     */
    @Attribute(name = "logs", docs = "Returns an array of all logs matching a given filter object.")
    public Flux<Val> ethGetLogs(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withLogs(leftHandValue), variables);
    }

    private Callable<Val> withLogs(Val saplObject) {
        return () -> toVal(web3j.ethGetLogs(getEthFilterFrom(saplObject.get())).send().getLogs());
    }

    /**
     * Method to get a List of the current block hash, the seed hash and the
     * difficulty.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Array Nodes each holding the three values.
     */
    @Attribute(name = "work", docs = "Returns the hash of the current block, the seedHash, and the boundary condition to be met (\"target\").")
    public Flux<Val> ethGetWork(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withWork(), variables);
    }

    private Callable<Val> withWork() {
        return () -> toVal(web3j.ethGetWork().send().getResult());

    }

    /**
     * Method for querying the current whisper protocol version.
     *
     * @param leftHandValue is unused here
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes where each contains the whisper protocol
     *         version.
     */
    @Attribute(name = "shhVersion", docs = "Returns the current whisper protocol version.")
    public Flux<Val> shhVersion(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withShhVersion(), variables);
    }

    private Callable<Val> withShhVersion() {
        return () -> toVal(web3j.shhVersion().send().getVersion());
    }

    /**
     * Method to verify if the client has the private keys for a certain identity.
     *
     * @param leftHandValue needs to be the public address of the identity.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes returning true if the client holds the private
     *         keys and false otherwise.
     */
    @Attribute(name = "hasIdentity", docs = "Checks if the client holds the private keys for a given identity.")
    public Flux<Val> shhHasIdentity(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withHasIdentity(leftHandValue), variables);
    }

    private Callable<Val> withHasIdentity(Val saplObject) {
        return () -> toVal(web3j.shhHasIdentity(saplObject.get().textValue()).send().getResult());
    }

    /**
     * Method for getting all new shh messages grouped in arrays. Each array holds
     * the new messages that appeared since the last array.
     *
     * @param leftHandValue should simply be the filter id as BigInteger value.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes each containing an array of Messages.
     */
    @Attribute(name = "shhFilterChanges", docs = "Polling method for whisper filters. Returns new messages since the last call of this method.")
    public Flux<Val> shhGetFilterChanges(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withShhFilterChanges(leftHandValue), variables);
    }

    private Callable<Val> withShhFilterChanges(Val saplObject) {
        return () -> toVal(web3j.shhGetFilterChanges(saplObject.get().bigIntegerValue()).send().getMessages());
    }

    /**
     * Method for getting all shh messages from a certain filter.
     *
     * @param leftHandValue should simply be the filter id as BigInteger value.
     * @param variables     can optionally contain a key with value
     *                      "ethPollingInterval" that holds the time span in which
     *                      the blockchain should be polled in milliseconds
     * @return A Flux of Json Nodes each containing all messages matching the
     *         requested filter.
     */
    @Attribute(name = "messages", docs = "Get all messages matching a filter. Unlike shhFilterChanges this returns all messages.")
    public Flux<Val> shhGetMessages(Val leftHandValue, Map<String, JsonNode> variables) {
        return scheduledFlux(withShhMessages(leftHandValue), variables);
    }

    private Callable<Val> withShhMessages(Val saplObject) {
        return () -> toVal(web3j.shhGetMessages(saplObject.get().bigIntegerValue()).send().getMessages());
    }

    private Flux<Val> scheduledFlux(Callable<Val> functionToCall, Map<String, JsonNode> variables) {
        Flux<Long> timer = Flux.interval(Duration.ZERO, getPollingInterval(variables));
        return timer.flatMap(i -> Mono.fromCallable(functionToCall)).distinctUntilChanged().onErrorReturn(Val.NULL);
    }

    private static Duration getPollingInterval(Map<String, JsonNode> variables) {
        if (variables != null) {
            JsonNode ethPipConfig = variables.get(ETH_PIP_CONFIG);
            if (ethPipConfig != null) {
                JsonNode pollingInterval = variables.get(ETH_POLLING_INTERVAL);
                if (pollingInterval != null && pollingInterval.isLong())
                    return Duration.ofMillis(pollingInterval.asLong(DEFAULT_ETH_POLLING_INTERVAL));
            }
        }
        return Duration.ofMillis(DEFAULT_ETH_POLLING_INTERVAL);
    }

}
