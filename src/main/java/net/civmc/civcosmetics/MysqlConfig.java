package net.civmc.civcosmetics;

import org.bukkit.configuration.ConfigurationSection;

record MysqlConfig(
    boolean enabled,
    String host,
    int port,
    String database,
    String username,
    String password,
    boolean useSsl,
    int connectionTimeoutMs
) {

    static MysqlConfig from(ConfigurationSection section) {
        if (section == null) {
            return new MysqlConfig(true, "localhost", 3306, "minecraft", "minecraft", "", false, 5000);
        }
        String database = section.getString("dbname", section.getString("database", "minecraft"));
        return new MysqlConfig(
            section.getBoolean("enabled", true),
            section.getString("host", "localhost"),
            section.getInt("port", 3306),
            database,
            section.getString("username", "minecraft"),
            section.getString("password", ""),
            section.getBoolean("use-ssl", false),
            section.getInt("connection-timeout-ms", 5000)
        );
    }

    String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database
            + "?useSSL=" + useSsl
            + "&connectTimeout=" + connectionTimeoutMs
            + "&socketTimeout=" + Math.max(connectionTimeoutMs, 5000);
    }
}
