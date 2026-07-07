package com.example.api.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * UserRepository のテストで DynamoDB Local コンテナを 1 つだけ共有するための Singleton Container パターン。
 *
 * <p>docker-compose.yml で使っている amazon/dynamodb-local イメージと同じものを使用し、
 * ローカル開発と同じ挙動（DynamoDBMapper / 低レベル API 双方）を検証する。
 */
public interface DynamoDbContainerSupport {

    int DYNAMODB_PORT = 8000;

    GenericContainer<?> DYNAMODB_CONTAINER = createContainer();

    private static GenericContainer<?> createContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest"))
                .withExposedPorts(DYNAMODB_PORT)
                .withCommand("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory")
                .waitingFor(Wait.forListeningPort());
        container.start();
        return container;
    }
}
