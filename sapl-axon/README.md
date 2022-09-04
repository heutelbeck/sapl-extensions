# SAPL Axon Framework Integration

This library supports the implementation of Attribute-based Access Control (ABAC) and Attribute Stream-based Access Control (ASBAC) in applications using the Axon Framework. 

## What is Attribute-based Access Control?

Attribute-based Access Control (ABAC) is an expressive access control model. 

![ABAC](assets/abac.png)

ABAC decides on granting access by inspecting attributes of the subject, resource, action, and environment. 

The subject is the user or system requesting access to a resource. Attributes may include information such as the user's department in an organization, a security clearance level, schedules, location, or qualifications in the form of certifications.

The action is how the subject attempts to access the resource. An action may be one of the typical CRUD operations or something more domain-specific like "assign new operator," and attributes could include parameters of the operation.

Resource attributes may include owners, security classification, categories, or other arbitrary domain-specific data.

Environment attributes include data like the system and infrastructure context or time.

An application performing authorization of an action formulates an authorization question by collecting attributes of the subject, action, resource, and environment as required by the domain and asks a decision-making component which then makes a decision based on domain-specific rules, which the application then has to enforce.

### The SAPL Attribute-Based Access Control (ABAC) Architecture

SAPL implements its interpretation of ABAC called Attribute Stream-Based Access Control (ASBAC). It uses publish-subscribe as its primary mode of interaction between the individual components. This tutorial will explain the basic ideas. The [SAPL Documentation](https://sapl.io/docs/2.1.0-SNAPSHOT/sapl-reference.html#reference-architecture) provides a complete discussion of the architecture. 

![SAPL ABAC/ASBAC Architecture](assets/sapl-architecture.png)

In your application, there will be several code paths where a subject attempts to perform some action on a resource, and based on the domain's requirements, the action must be authorized. For example, in a zero-trust system, all actions triggered by users or other components must be explicitly authorized. 

A *Policy Enforcement Point (PEP)* is the logic in your application in these code paths that do:
* mediate access to the *Resource Access Point (RAP)*, i.e., the component executing the action and potentially retrieving data 
* formulate the authorization question in the form of an *authorization subscription*, i.e., a JSON object containing values for the subject, resource, action, and possibly the environment. The PEP determines the values based on the domain and context of the current attempt to execute the action.
* delegates the decision-making for the authorization question to the *Policy Decision Point (PDP)* by subscribing to it using the authorization subscription.
* enforces all decisions made by the PDP.

## Maven Dependencies and Project Setup

The SAPL Axon extension currently resides in the ```2.1.0-SNAPSHOT``` version of SAPL. For Maven to be able to download the respective libraries, add the central snapshot repository to the POM:

```xml
    <repositories>
        <repository>
            <id>ossrh</id>
            <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```

SAPL provides a bill of materials module, helping you to use compatible versions of SAPL modules. After adding the following to your POM, future dependencies can omit  the ```<version>``` tag of individual SAPL dependencies:

```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.sapl</groupId>
                <artifactId>sapl-bom</artifactId>
                <version>2.1.0-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```
 
For an application using SAPL, its project requires two components. First, the so-called policy decision point (PDP) needs a component for making authorization decisions. SAPL supports embedding the PDP within your application or using a dedicated server application and delegating the decision-making to this remote service. An embedded PDP makes decisions locally based on policies stored in the application resources. The following dependency is responsible:

```xml
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-spring-pdp-embedded</artifactId>
        </dependency>
```

For connecting to using PDP servers, use ```<artifactId>sapl-spring-pdp-embedded</artifactId>```.


SAPL provides deep integration with Axon and Spring Security. This integration enables simple deployment of policy enforcement points in Spring application using a declarative aspect-oriented programming style. Add the following dependency to your project:

```xml
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-axon</artifactId>
        </dependency>
```

Finally, the embedded PDP (in its default configuration) requires a folder in the resources ```src/main/resources``` called ```policies```, and a configuration file called ```pdp.json```. To start, add the following content to this file: 

```json
{
    "algorithm": "DENY_UNLESS_PERMIT",
    "variables": {}
}
```

The ```algorithm``` property selects an algorithm used to resolve conflicting results from policy evaluation. In this case, the algorithm will ensure that the PDP always returns a ```deny``` decision if no policy evaluation returns an explicit ```permit``` decision. You can use the ```variables``` property to define environment variables, e.g., the configuration of policy information points (PIPs). All policies can access the content of these variables.

## Securing Axon Applications with SAPL

Just like Axon itself, SAPL supports the CQRS-ES pattern, and it is possible to secure access to the command end query side independently. However, for both sides to be secured, authentication of the users triggering both commands and queries is a prerequisite. 

### Authentication of Commands and Queries

By default, the SAPL Axon extension will use Spring autoconfiguration to deploy some infrastructure code. This configuration includes the ```AuthenticationCommandDispatchInterceptor``` and the ```AuthenticationQueryDispatchInterceptor```. 
These ```MessageDispatchInterceptor``` implementations are responsible for adding authentication information to any command or query message before dispatching it to the respective bus. 

These interceptors add the authenticated user to the message metadata. The key to identifying the user is ```subject```. The SAPL extension expects the value to be a valid JSON string. The default implementation uses the default ```ObjectMapper``` deployed in the application context to write the ```Authentication``` including the ```Principal``` object to this field. It also removes ```credentials``` and ```password``` field from the objects before adding the objects to the metadata. 

To customize this behavior the developer can supply an ```AuthenticationSupplier``` Bean.

### Securing the Command Side

Establishing a Policy Enforcement Point (PEP) for a command is straightforward. It only requires the addition of a single ```@PreHandleEnforce```annotation on the method carrying the Axon annotation ```@CommandHandler```, independently if this method resides within an aggregate or in a domain service. 

```java
	@CommandHandler
	@PreHandleEnforce
	void handle(HospitalisePatient cmd) {
		apply(new PatientHospitalised(cmd.id(), cmd.ward()));
	}
```

Whenever this annotation is present on a ```@CommandHandler```, a PEP is wrapped around the invocation of the command handler. Upon receiving a command, after the potential replay of an aggregate, and before calling the ```handle``` method, this PEP constructs an ```AuthorizationSubscription```, and gets a single ```AuthorizationDecision``` from the Policy Decision Point (PDP). Depending on the decision, i.e., is it ```PERMIT``` or not, the ```handle``` method is invoked, or it denies access. Further, any additional constraints, i.e., obligations or advice, are attempted to be enforced. Failure to enforce obligations will also result in the PEP denying access, i.e., failing the command execution with an ```AccessDeniedException```. 

Without further information, the PEP has to make the best effort to formulate a meaningful authorization subscription based on the technical information available. So it will attempt to add all available information about the command and the target aggregate to the subscription. In the case of the command above, this could look like this:

```JSON
{
  "subject": {
    "username": "cheryl",
    "authorities": [],
    "accountNonExpired": true,
    "accountNonLocked": true,
    "credentialsNonExpired": true,
    "enabled": true,
    "assignedWard": "ICCU",
    "position": "DOCTOR"
  },
  "action": {
    "actionType": "command",
    "commandName": "io.sapl.demo.axon.command.PatientCommandAPI$HospitalisePatient",
    "payload": {
      "id": "0",
      "ward": "ICCU"
    },
    "payloadType": "io.sapl.demo.axon.command.PatientCommandAPI$HospitalisePatient",
    "metadata": {
      "subject": "{\"username\":\"cheryl\",\"authorities\":[],\"accountNonExpired\":true,\"accountNonLocked\":true,\"credentialsNonExpired\":true,\"enabled\":true,\"assignedWard\":\"ICCU\",\"position\":\"DOCTOR\"}"
    }
  },
  "resource": {
    "aggregateType": "Patient",
    "aggregateIdentifier": "0"
  }
}
```

To make these authorization questions more domain-specific, and thus the policies to be written closer to the domain's ubiquitous language, developers may customize the authorization subscription by providing explicit [Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/expressions.html) expressions as parameters in the annotation:

```java
	@CommandHandler
	@PreHandleEnforce(action = "{'command':'HospitalisePatient', 'ward':#command.ward()}", resource = "{ 'type':'Patient', 'id':id, 'ward':ward }")
	void handle(HospitalisePatient cmd) {
		apply(new PatientHospitalised(cmd.id(), cmd.ward()));
	}
```

These SpEL expressions will make different objects available for the construction of the authorization subscription. The command will be available as ```#command```, the complete ```CommandMessage``` is available as ```#message```, the metadata map as ```#metadata```, the ```Executable```refers to the handler method as ```#executable``` and the aggregate, or domain service Bean are set as the root object of the SpEL evaluation context and its members and method are directly accessible, if public.

In the example above, the action SpEL accesses the command's ```ward()``` method. Also, in the resource expression, the member variables ```id``` and ```ward``` of the aggregate are directly accessed. Note that for this to be possible, the respective fields must be ```public```. 

The resulting authorization subscription could look like this:

```JSON
{
  "subject": {
    "username": "cheryl",
    "authorities": [],
    "accountNonExpired": true,
    "accountNonLocked": true,
    "credentialsNonExpired": true,
    "enabled": true,
    "assignedWard": "ICCU",
    "position": "DOCTOR"
  },
  "action": {
    "command": "HospitalisePatient",
    "ward": "ICCU"
  },
  "resource": {
    "type": "Patient",
    "id": "0",
    "ward": "NONE"
  }
}
```

And a matching SAPL policy may look like this:

```
policy "only doctors may hospitalize patients but only into their wards, the system may do it as well."
permit 	action.command == "HospitalisePatient"
where 
  subject == "SYSTEM" || (subject.position == "DOCTOR" && action.ward ==  subject.assignedWard);
```

### Securing the Query Side

The ```@QueryHandler``` methods can be secured similarly to the command side by adding SAPL annotations. However, for the query side, there are four different types of PEPs. Each PEP type corresponds to different annotations. Also, these annotations behave differently if the query is a normal or a subscription query.

#### Security Annoatrions and Non-Subscription Queries

- ```@PreHandleEnforce```: Established a PEP that constructs the authorization subscription and gets a decision *before* invoking the ```@QueryHandler``` method. 
- ```@PostHandleEnforce```: Established a PEP that constructs the authorization subscription and gets a decision *after* invoking the ```@QueryHandler``` method. Developers can use this annotation if the query result is required to construct the authorization subscription. The query result is available as ```#queryResult``` for SpEL expression in the annotation.
- ```@EnforceDropUpdatesWhileDenied```: An annotation typically used for subscription queries. It falls back to the behavior of ```@PreHandleEnforce``` if it encounters a non-subscription query at runtime.
- ```@EnforceRecoverableUpdatesIfDenied```: An annotation typically used for subscription queries. It falls back to the behavior of ```@PreHandleEnforce``` if it encounters a non-subscription query at runtime.

### Security Annotations and Subscription Queries

- ```@PreHandleEnforce```: Established a PEP that constructs the authorization subscription and gets a decision *before* invoking the ```@QueryHandler``` method for the initial query result. If the initial decision of the PDP is *not* ```PERMIT```, access is denied to both the initial result and the updates. If the initial decision is ```PERMIT```, the initial result is delivered, and updates are starting to deliver until a first decision implying ```DENY``` is sent by the PDP. Then updates are stopped, and the PEP terminates the query.
- ```@PostHandleEnforce```: The post invocation idea does not translate well to subscription queries, and access is denied by default. We suggest having dedicated queries for subscription and non-subscription queries if the domain requires different PEPs for the different types of queries.
- ```@EnforceDropUpdatesWhileDenied```: This annotation will not deliver and drop updates when the last known decision is implying ```DENY```, but it will not cancel the query. It will resume sending updates when the PDP sends a new ```PERMIT```decision.
- ```@EnforceRecoverableUpdatesIfDenied```: This annotation will not deliver and drop updates when the last known decision is implying ```DENY```, but it will not cancel the query. It will resume sending updates when the PDP sends a new ```PERMIT```decision. Additionally, the fact that access is denied will be sent to the client that it is aware of this fact. This PEP requires the use of the ```SaplQueryGateway``` to send recoverable queries.

The first three PEPs are straightforward forward, and the subscription can be customized using SpEL in the same way as for queries. For the ```@EnforceRecoverableUpdatesIfDenied```, the client application has to do a few more steps to react on access denied events in the update stream. As Axon terminates a subscription query, whenever it sends an exception in a subscription query response, the updates have to be wrapped in a dedicated event and be unwrapped at the client side. Also, the client side has to explicitly signal to the query handling side that PEP must wrap the updates.

To do so, the client sends the query via the ```SaplQueryGateway```, which signals the request to the query sinde and unwraps the updates transparently for the client. The client now can decide to continue to stay subscribed by using ```onErrorContinue``` on the update ```FLux```.

```java
  @Autowired
  SaplQueryGateway queryGateway;
  
  /* ... */
  
  var result = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
     instanceOf(String.class), instanceOf(String.class), () -> { /* Runnable on deny */});

  result.initialResult().subscribe(this::handleInitialResult);
  result.updates().onErrorContinue((t, o) -> accessDeniedHandler.run()).subscribe(this::handleUpdates);
```

### Constraint Handling - Obligations and Advice

SAPL allows the PDP to make decisions that only grant access under certain additional constraints that the PEP must fulfill (i.e., obligations) or should fulfill (i.e., advice). Each constraint is expressed as an arbitrary JSON value in the decisions obligations or advice field. The PEP must deny access whenever any obligation is present, and the PEP has no way to fulfill the requested constraint.

The automatically created PEPs of the SAPL Axon extension support the insection of handlers for constraints at different points in the execution paths of commands and queries. There are two basic categories of constraint handlers. Constraint handler methods are annotated by the ```@ConstraintHandler``` annotations and constraint handler provider beans.

#### Constraint Handler Provider Beans

A constraint handler provider bean is a factory bean creating concrete constraint handlers for specific constraints. All of these beans implement the ```Responsible``` interface, which offers the method ```boolean isResponsible(JsonNode constraint)```. Whenever the PDP sends a decision to a PEP, the PEP asks all handler provider beans if they are responsible for any given constraint in the decision. A handler provider is responsible for a constraint if its ```isResponsible``` method returns  ```true``` for the constraint. If the matching handler is type-dependent, its handler provider implements the ```TypeSupport```or ```ResponseTypeSupport``` interface to enable the PEP to check for type compatibility. 

Once the PEP identifies a handler provider as responsible (```isResponsible``` returns ```true``` and the types involved are compatible), the PEP asks the handler provider to generate a constraint handler for the constraint. Typically such a handler is something like a ```Runnable```, ```Consumer```, or ```Function```. Finally, the PEP hooks the handlers into the command or query handling execution path.

The different Axon-specific handler provider interfaces reside in the package ```io.sapl.constrainthandling.api```:

- ```CommandConstraintHandlerProvider``` returns a ```Function<CommandMessage<?>, CommandMessage<?>>``` to either trigger side effects based on the command content or to modify the command before handling. To implement a ```CommandConstraintHandlerProvider``` the interface offers several utility methods that developers can use if they only want to modify a specific part of the query, e.g., ``` Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint)``` to modify the contents of the payload of the message.
- ```QueryConstraintHandlerProvider``` returns a ```Function<QueryMessage<?>, QueryMessage<?>>``` to either trigger side effects based on the query content or to modify the query before handling. To implement a ```QueryConstraintHandlerProvider``` the interface offers several utility methods that developers can use if they only want to modify a specific part of the query, e.g., ``` Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint)``` to modify the contents of the payload of the message.
- ```OnDecisionConstraintHandlerProvider```returns a ```BiConsumer<AuthorizationDecision, Message<?>>``` which is executed whenever the PDP returns a new decision.
- The ```ResultConstraintHandlerProvider``` returns a ```Function<Object, Object>``` which is applied to query result messages.
- The ```UpdateFilterConstraintHandlerProvider```filters update messages of subscription queries. It returns a ```Predicate<ResultMessage<?>>```; if present, only updates satisfying the predicate are sent downstream.
- The ```CollectionAndOptionalFilterPredicateProvider``` is a sub-type of ```ResultConstraintHandlerProvider```. The developer can implement the ```boolean test(T o, JsonNode constraint)``` method. And when a query returns an ```Iterable```, ```array```, or ```Optional```. The handler will remove all elements that do not satisfy this predicate.

Further, there are the following interfaces from ```io.sapl.spring.constraints.api``` which are used in the Axon extension as well:

- The ```MappingConstraintHandlerProvider``` returns a ```Function<T, T>``` used to modify command results.
- The ```ErrorMappingConstraintHandlerProvider``` returns a ```Function<Throwable, Throwable>``` used to modify errors before sending them downstream.


#### Modifying Response Messages with the ```ResponseMessagePayloadFilterProvider```

The Axon Extension comes with a default constraint handler provider pre-configured. The ```ResponseMessagePayloadFilterProvider``` can be used to modify the payload of ```ResponseMessage```s. The JSON constraint may specify a number of modification actions that indicate to operate on a node in the JSON document identified by a JSONPath expression. A policy triggering this handler may look like this: 

```
policy "authenticated users may see filtered." 
permit subject != "anonymous"
obligation
{
  "type"    : "filterMessagePayloadContent",
  "actions" : [
                { 
		  "type" : "blacken", 
		  "path" : "$.latestIcd11Code", 
		  "discloseLeft": 2
		},
		{ 
		  "type" : "delete", 
		  "path" : "$.latestDiagnosisText"
		},
		{ 
		  "type" : "replace", 
		  "path" : "$.name",
		  "replacement" : "[Name Hidden]"
		}
              ]
	}
```

This handler is triggered when the constraint is a JOSN Object, where  ```type``` equals ```filterMessagePayloadContent```. Then the array ```actions``` indicated a sequence of modifications to be made to the payload. The action ```replace``` attempts to replace the sub-section of the payload indicated by the JSONPath expression with the content of ```replacement```. The action ```delete``` sets the selected field to ```null```. The action ```blacken``` assumes the indicated value is a string, and the parts not excluded by ```discloseLeft``` or ```discloseRight``` are replaced with the character in ```replacement``` (defaulting to an Unicode square). 

This handler attempts to map the payload to JSON, then applies the actions and attempts to map it map to the original class. 

If encountering an ```Optional```, ```Iterable```, or ```array```, the actions are applied to the individual objects in these container classes.

#### ```@ConstraintHandler``` Methods on Aggregates and Services

There are use-cases, where a constraint handler requires access to the aggregate handling a command. For example, an obligation may imply the need to apply additonal events to the aggregate in the context of the running ```UnitOfWork```, or the handler may require aggregate state information. 

This can be achieved by adding methods annotated with ```@ConstraintHandler``` to the aggregate. Note that this also works for domain services with command handlers. 

```java
  @ConstraintHandler("#constraint.get('type').textValue() == 'documentSuspisiousManipulation'")
  public void handleSuspiciousManipulation(JsonNode constraint) {
    apply(new SuspiciousManipulation(id, constraint.get("username").textValue()));
  }
```

Similar to the constraint handler provider beans, first the PEP will have to determine if a ```@ConstraintHandler``` is responsible to handle any given constraint. To do so, the PEP inspects the object determined to have the matching ```@CommandHandler``` method. For each such method it evaluates the SpEL expression in the ```ConstraintHandler``` annotation. This method is consideren responsible when the expression is empty, or if it evaluates to ```true```. In the evaluation context of the expression, the object handling the command is the root object. The variable ```#constraint``` is set to the JSON value representing the constraint. The variable ```#command```is set to the command message.

These constraint handlers are invoked immediately before invoking the command handler. The method may have optional parameters which are resolved like parameters of command and event handlers in Axon, including the constraint as a ```JsonNode```, the command payload, or the ```AuthorizationDecision```.
