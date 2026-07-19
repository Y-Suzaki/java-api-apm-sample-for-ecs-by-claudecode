package com.example.api.support;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Repository テスト間で MySQL コンテナを 1 つだけ共有するための Singleton Container パターン。
 *
 * <p>実装クラスの static 初期化時にコンテナを起動し、明示的には停止しない
 * （JVM 終了時に Ryuk が後始末する）ことで、テストクラスをまたいだコンテナ再起動コストを避ける。
 *
 * <p>docker-compose.yml と同じ mysql/init/01_init.sql をそのままコンテナへコピーして実行することで、
 * 本番と同じスキーマ定義（DATETIME(6) や外部キー制約を含む）でリポジトリの挙動を検証する。
 */
public interface MySqlContainerSupport {

    MySQLContainer MYSQL_CONTAINER = createContainer();

    private static MySQLContainer createContainer() {
        MySQLContainer container = new MySQLContainer("mysql:8.0")
                .withDatabaseName("sampledb")
                .withUsername("appuser")
                .withPassword("apppassword")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("mysql/init/01_init.sql"),
                        "/docker-entrypoint-initdb.d/01_init.sql");
        container.start();
        return container;
    }
}
