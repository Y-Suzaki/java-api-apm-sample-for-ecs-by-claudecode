package com.example.api.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import feign.FeignException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IpifyClient(Feign Client)の実際の Spring Cloud OpenFeign 配線を通した契約テスト。
 *
 * <p>実際の ipify.org には接続せず、WireMock でスタブ化したローカル HTTP サーバーに対して
 * Feign が JSON をどう送受信するか、エラー時に FeignException が伝播するかを検証する。
 * DynamoDB / MySQL への接続は不要なため、それらの自動構成は除外した最小構成でコンテキストを起動する。
 */
@SpringBootTest(
        classes = IpifyClientTest.FeignTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IpifyClientTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private IpifyClient ipifyClient;

    @DynamicPropertySource
    static void registerIpifyUrl(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        registry.add("external.ipify.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @Test
    void getPublicIp_parsesJsonResponseFromExternalApi() {
        wireMockServer.stubFor(get(anyUrl())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ip\":\"203.0.113.1\"}")));

        IpifyClient.IpifyResponse response = ipifyClient.getPublicIp("json");

        assertThat(response.ip()).isEqualTo("203.0.113.1");
        wireMockServer.verify(getRequestedFor(anyUrl()).withQueryParam("format", equalTo("json")));
    }

    @Test
    void getPublicIp_whenExternalApiReturns500_throwsFeignException() {
        wireMockServer.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> ipifyClient.getPublicIp("json"))
                .isInstanceOf(FeignException.class);
    }

    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class
    })
    @EnableFeignClients(clients = IpifyClient.class)
    @Configuration
    static class FeignTestConfig {
    }
}
