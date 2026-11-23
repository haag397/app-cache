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

The single workflow in `.github/workflows/ci.yml` builds the application and
runs unit and integration tests with Java 21 on pull requests targeting main
and pushes to main. Testcontainers uses Docker on the GitHub-hosted runner.

To block merges when tests fail, require the **Verify Java 21** status check
in a branch rule for main. Require branches to be up to date before merging,
so the tested changes include the latest main.

After verification succeeds, a separate job requests squash auto-merge for
non-draft pull requests created by the repository owner from this repository.
It matches the head commit to avoid merging a different revision. The job has
write permission and does not check out or run application code. Fork pull
requests are tested but do not enter the merge job.

Before running this workflow, enable **Allow squash merging** and
**Allow auto-merge** in Settings → General → Pull Requests. Repository or
organization policies must also permit the workflow token to merge pull
requests. Required reviews and other branch rules still apply. Conflicts or
failed checks require a fix. Changes must be pushed and a pull request opened
before automation runs. The workflow does not deploy to a server.
