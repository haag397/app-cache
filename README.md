# learn-cache

A Spring Boot lab for opening, reading, and closing demo deposits. PostgreSQL
stores the deposits. Run the same API with Caffeine inside the JVM or with Redis
to compare the cache behavior.

## Start the lab

Requires JDK 21 and Docker Compose. Run these commands from the repository root:

```sh
docker compose up -d --wait
./mvnw spring-boot:run
```

The default is Caffeine (up to 1,000 entries). Stop the application and run
with CACHE_TYPE=redis to use the shared cache instead:

```sh
CACHE_TYPE=redis ./mvnw spring-boot:run
```

In IntelliJ, set the environment variable `CACHE_TYPE=redis` for Redis.
The default value is `caffeine`. Both providers expire entries 60 seconds after a write. Set
`CACHE_TTL=5s` when launching the application to test expiry quickly. PostgreSQL
and Redis listen on localhost ports 15432 and 16379, respectively. If their ports are in use, set the same
`POSTGRES_PORT` or `REDIS_PORT` when starting Compose and the app.

## Try the cache scenarios

Open a deposit and copy the returned `id`:

```sh
curl -sS -X POST http://localhost:8080/deposits   -H 'Content-Type: application/json'   -d '{"customerName":"Sara","amount":2500.00}'
```

Use that ID in the following requests. Watch `logs/app.log` for Spring cache
traces and the `Loading deposit … from the database` message.

| Request | Expected behavior |
| --- | --- |
| First `GET /deposits/{id}` | **Miss**. `@Cacheable` runs the service method, reads PostgreSQL, then caches the result. |
| Repeat the same GET before expiry | **Hit**. Spring returns the cached deposit and skips the method and database read. |
| `GET /deposits/{id}?manual=true` | The same cache-aside pattern with Spring's `Cache` API. Try after eviction, then repeat to see both paths. |
| `POST /deposits?warm=true` with the same JSON body | Opens a **new** deposit in PostgreSQL and preloads its cache entry with `@CachePut`. Its first GET is a hit. |
| `PUT /deposits/{id}/cache` | `@CachePut` always reloads from PostgreSQL and replaces the cached value, even on a hit. |
| `DELETE /deposits/{id}/cache` | `@CacheEvict` removes only the entry. The next GET loads the deposit from PostgreSQL. |
| Wait past the expiry and GET again | **TTL expiry**. The next GET misses and populates the cache again. |
| `DELETE /deposits/{id}`, then GET | Deletes from PostgreSQL and evicts the entry after successful deletion. GET returns 404, which is not cached. |

When using Redis, inspect entries and their remaining TTL:

```sh
docker compose exec redis redis-cli --scan --pattern 'learn-cache:deposits::*'
docker compose exec redis redis-cli TTL 'learn-cache:deposits::<id>'
```

Caffeine is private to one JVM and starts empty on restart. Redis can share
entries between app instances and survive an app restart until TTL expiry.
This lab selects one provider per run. Cache aside is the loading pattern, hit
and miss are lookup outcomes, and put and evict are cache operations. The warm
opening writes to the database first and then updates the cache synchronously.
The two writes are independent, so this demo does not provide atomic consistency
across the database and cache under concurrent operations or failures.

Stop the lab services with `docker compose down`. The database volume is retained.

## Tests

| Command | What runs |
| --- | --- |
| `./mvnw test` | Unit tests with JUnit Jupiter and Mockito. No Docker or Spring context required. |
| `./mvnw verify` | Unit and integration tests. Requires a running Docker engine. Testcontainers starts and cleans up its own PostgreSQL and Redis containers on mapped ports. |
| `./mvnw verify -Dit.test=RedisDepositCacheIT` | Unit tests and the Redis integration suite only. |

In IntelliJ you can run any of these classes as a JUnit test. Enable Docker
before running an `*IT` class. Maven uses Surefire for `*Test` and Failsafe for
`*IT`. Their reports are in `target/surefire-reports` and
`target/failsafe-reports`.

- **DepositServiceTest** checks the rules for opening a deposit and the manual
  cache-aside logic. Mockito stands in for the repository. An in-memory map
  provides real cache state. JUnit parameterized tests cover invalid inputs
  and verify that none causes a write.
- **CaffeineDepositCacheIT and RedisDepositCacheIT** run the same HTTP scenarios
  via MockMvc and a real Spring context. They check cache-aside reads, warm
  opening, refresh, eviction, and 400/404 responses. PostgreSQL is real for
  both providers. Some tests change a row directly to prove that a hit returns
  the cached amount and that eviction or refresh exposes the new amount.
- **RedisDepositCacheIT** also reads the stored JSON and checks that Redis
  assigned a TTL. It shortens one key's TTL and uses Awaitility to observe
  expiry and verify that the next read repopulates the entry.

`@ServiceConnection` supplies the container connection details to Spring Boot,
independently of the addresses in Compose and any locally installed services.
The cache annotations are exercised through the Spring proxy in the integration
tests. The unit tests instantiate the service directly and cover behavior that
does not require that proxy. MockMvc exercises Spring MVC without starting a
network server.

## Logs

Spring Boot writes application logs to `logs/app.log` as well as the console.
To save Maven output when running a lifecycle command in a Bash terminal:

```sh
mkdir -p logs
set -o pipefail
./mvnw test 2>&1 | tee logs/maven.log
```

Replace `test` with the lifecycle phase or goal you want to run. Logs are
local files and are not tracked by Git.

## GitHub Actions

The single workflow in `.github/workflows/ci.yml` requests squash auto-merge for
non-draft pull requests targeting main, created by the repository owner from
this repository. It checks that the head commit has not changed before
requesting the merge.

Run `./mvnw clean verify` locally before pushing. GitHub does not run the build
or tests, and the merge job cannot verify that local tests passed.

Enable **Allow squash merging** and **Allow auto-merge** under
Settings → General → Pull Requests. Repository or organization policies must
permit the workflow token to merge pull requests. Remove the old required
**Verify Java 21** check from any main branch protection rule or ruleset,
otherwise merges will wait for a check that is no longer produced.
Other required reviews and branch rules still apply.

Push the branch and open a pull request to start the automation. When all
configured merge requirements are met, GitHub can merge it automatically.
The workflow does not deploy to a server.

## SOAP deposit service

The REST API and SOAP endpoint use the same DepositService and selected cache
provider. SOAP 1.1 messages are accepted at `POST /soap`. Import
`http://localhost:8080/soap/deposits.wsdl` into SoapUI to explore all operations.

| Operation | Behavior |
| --- | --- |
| openDeposit | Opens a deposit in PostgreSQL and populates the selected cache. |
| getDeposit | Reads through the existing annotation-based cache-aside service. |
| closeDeposit | Deletes the deposit and evicts its cached value after successful deletion. |

The contract is `src/main/resources/soap/deposits-v1.xsd`, with the versioned
namespace `urn:learn-cache:deposits:v1`. Maven generates the JAXB request and
response types in `target/generated-sources/jaxb` during generate-sources.
Run `./mvnw generate-sources` and reload Maven if IntelliJ cannot resolve the
contract types. Generated sources are build outputs and should not be edited.

Start the application using the database and cache setup described above, then
send a SOAP request:

```sh
curl -i http://localhost:8080/soap \
  -H 'Content-Type: text/xml; charset=UTF-8' \
  --data-binary @- <<'XML'
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:d="urn:learn-cache:deposits:v1">
  <soap:Body>
    <d:openDepositRequest>
      <d:customerName>Sara</d:customerName>
      <d:amount>2500.25</d:amount>
    </d:openDepositRequest>
  </soap:Body>
</soap:Envelope>
XML
```

For lookup or closure, replace the element inside Body with one of these
payloads and use the id returned by opening:

```xml
<d:getDepositRequest><d:id>REPLACE_WITH_UUID</d:id></d:getDepositRequest>
```

```xml
<d:closeDepositRequest><d:id>REPLACE_WITH_UUID</d:id></d:closeDepositRequest>
```

Requests and responses are validated against XSD. Invalid names, amounts,
missing required fields, and malformed identifiers return a SOAP Client Fault.
A missing deposit also returns a Client Fault. Unexpected exceptions produce
a generic Server Fault while the exception is logged on the server. SOAP 1.1
faults use HTTP 500, so clients must inspect the fault code rather than
interpreting every HTTP 500 as a server-side application defect.

Run `./mvnw verify -Dit.test=DepositSoapIT` for the unit suite plus SOAP
integration tests. Docker is needed. These tests use a real HTTP server on
a random port and a Testcontainers PostgreSQL instance. They exercise WSDL
publication, schema validation, faults, and the open/read/close lifecycle.
They also prove that SOAP and REST share the same cached deposit.

### Interview discussion

- **Why contract first?** XSD defines allowed XML before endpoint code.
  JAXB generates Java DTOs from that contract and WSDL describes operations,
  messages, and the service address. A versioned namespace allows a future
  incompatible v2 contract without silently changing v1 clients.
- **How are requests routed?** `@Endpoint` registers the SOAP adapter,
  `@PayloadRoot` matches namespace and local element name, and
  `@RequestPayload` / `@ResponsePayload` bind XML through JAXB.
  `@RequiredArgsConstructor` injects the existing service.
- **Where do rules belong?** XSD validates message structure and simple
  constraints at the boundary. DepositService enforces the domain rules and
  persistence behavior shared by REST and SOAP.
- **Why map DTOs?** Generated SOAP types describe the wire contract.
  Mapping them to the existing deposit model keeps generated classes out
  of persistence and cache code.
- **How does caching work across protocols?** Both adapters call the same
  Spring service proxy, so the same cache annotations and keys apply.
- **What about retries?** Opening currently creates a fresh UUID every time.
  An automatic retry after a timeout can create a duplicate deposit.
  A real banking API needs a client request id and a database uniqueness
  constraint with transactional handling before safe automatic retries.
- **What is outside this lab?** Authentication, authorization, WS-Security,
  audit trails, concurrent balance operations, and atomic DB/cache consistency
  are not implemented. Contract validation and caching alone do not make
  this a production banking service.

References:
[Spring SOAP guide](https://spring.io/guides/gs/producing-web-service/)
and [Spring-WS reference](https://docs.spring.io/spring-ws/docs/current/reference/html/).
