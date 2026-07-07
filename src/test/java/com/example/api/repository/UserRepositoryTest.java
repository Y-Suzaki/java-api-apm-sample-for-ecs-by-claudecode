package com.example.api.repository;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.example.api.model.UserItem;
import com.example.api.support.DynamoDbContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserRepository を実 DynamoDB Local(Testcontainers)に対して検証するテスト。
 *
 * <p>Spring コンテキストは起動せず、DynamoDBMapper / AmazonDynamoDB を直接組み立てて
 * UserRepository をプレーンに new する。条件付き書き込み(attribute_not_exists / attribute_exists)
 * の実際の DynamoDB 挙動を検証することが目的のため、Mockito でのスタブ化はできない。
 */
class UserRepositoryTest implements DynamoDbContainerSupport {

    private static final String TABLE_NAME = "users";

    private static AmazonDynamoDB amazonDynamoDB;
    private static UserRepository userRepository;

    @BeforeAll
    static void setUpClient() {
        String endpoint = "http://" + DYNAMODB_CONTAINER.getHost() + ":"
                + DYNAMODB_CONTAINER.getMappedPort(DYNAMODB_PORT);

        amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "ap-northeast-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("dummy", "dummy")))
                .build();

        createTableIfNotExists();

        DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(amazonDynamoDB);
        userRepository = new UserRepository(dynamoDBMapper, amazonDynamoDB);
        ReflectionTestUtils.setField(userRepository, "tableName", TABLE_NAME);
    }

    private static void createTableIfNotExists() {
        ListTablesResult tables = amazonDynamoDB.listTables();
        if (tables.getTableNames().contains(TABLE_NAME)) {
            return;
        }
        amazonDynamoDB.createTable(new CreateTableRequest()
                .withTableName(TABLE_NAME)
                .withAttributeDefinitions(new AttributeDefinition("email", ScalarAttributeType.S))
                .withKeySchema(new KeySchemaElement("email", KeyType.HASH))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @AfterEach
    void clearTable() {
        ScanResult scanResult = amazonDynamoDB.scan(new ScanRequest().withTableName(TABLE_NAME));
        scanResult.getItems().forEach(item -> amazonDynamoDB.deleteItem(new DeleteItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(Map.of("email", item.get("email")))));
    }

    @Test
    void save_thenFindByEmail_returnsSavedItem() {
        userRepository.save(itemOf("taro@example.com", "Taro"));

        Optional<UserItem> found = userRepository.findByEmail("taro@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Taro");
    }

    @Test
    void save_whenEmailAlreadyExists_throwsConditionalCheckFailedException() {
        userRepository.save(itemOf("taro@example.com", "Taro"));

        assertThatThrownBy(() -> userRepository.save(itemOf("taro@example.com", "Jiro")))
                .isInstanceOf(ConditionalCheckFailedException.class);
    }

    @Test
    void findByEmail_whenNotFound_returnsEmpty() {
        assertThat(userRepository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void findAll_respectsLimit() {
        userRepository.save(itemOf("a@example.com", "A"));
        userRepository.save(itemOf("b@example.com", "B"));
        userRepository.save(itemOf("c@example.com", "C"));

        List<UserItem> result = userRepository.findAll(2);

        assertThat(result).hasSize(2);
    }

    @Test
    void update_changesNameAndUpdatedAt_butKeepsCreatedAt() {
        UserItem item = itemOf("taro@example.com", "Taro");
        userRepository.save(item);

        UserItem updated = userRepository.update("taro@example.com", "Jiro", "2030-01-01T00:00:00Z");

        assertThat(updated.getName()).isEqualTo("Jiro");
        assertThat(updated.getUpdatedAt()).isEqualTo("2030-01-01T00:00:00Z");
        assertThat(updated.getCreatedAt()).isEqualTo(item.getCreatedAt());
    }

    @Test
    void update_whenEmailNotFound_throwsConditionalCheckFailedException() {
        assertThatThrownBy(() -> userRepository.update("missing@example.com", "Jiro", "2030-01-01T00:00:00Z"))
                .isInstanceOf(ConditionalCheckFailedException.class);
    }

    private static UserItem itemOf(String email, String name) {
        UserItem item = new UserItem();
        item.setEmail(email);
        item.setName(name);
        item.setCreatedAt("2024-01-01T00:00:00Z");
        item.setUpdatedAt("2024-01-01T00:00:00Z");
        return item;
    }
}