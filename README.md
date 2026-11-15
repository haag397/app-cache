# learn-cache

A Spring Boot project for learning about caching.

## Requirements

- JDK 21

## Run

```sh
./mvnw spring-boot:run
```

## Test

```sh
./mvnw test
```

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
