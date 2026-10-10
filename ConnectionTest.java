import java.sql.*;

/**
 * Standalone JDBC connectivity smoke-test.
 * Run with: java -cp <fat-jar> ConnectionTest
 * (or via Maven exec plugin with env vars set)
 */
public class ConnectionTest {
    public static void main(String[] args) throws Exception {
        String url  = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");

        System.out.println("Connecting to: " + url);
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT version()")) {
            if (rs.next()) {
                System.out.println("SUCCESS! PostgreSQL version: " + rs.getString(1));
            }
        }
    }
}
