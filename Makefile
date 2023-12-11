.PHONY: image run clean
all: run

run: image
	docker run --rm -it duckdb_issue

jar: target/test-1.0-SNAPSHOT-jar-with-dependencies.jar


target/test-1.0-SNAPSHOT-jar-with-dependencies.jar: src/main/java/MemoryLeak.java
	mvn package

image: jar
	docker build -t duckdb_issue .

clean:
	docker image rm duckdb_issue
	mvn clean
