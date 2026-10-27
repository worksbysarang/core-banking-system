package com.bank.system.dao;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.exception.BankingException;
import com.bank.system.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-Access Object for the {@code customers} table.
 */
public class CustomerDAO {

    private static final Logger log = LoggerFactory.getLogger(CustomerDAO.class);

    private static final String SQL_INSERT =
        "INSERT INTO customers (first_name, last_name, email, phone, national_id, address) " +
        "VALUES (?, ?, ?, ?, ?, ?) RETURNING customer_id";

    private static final String SQL_FIND_BY_ID =
        "SELECT customer_id, first_name, last_name, email, phone, national_id, address, created_at " +
        "  FROM customers WHERE customer_id = ?";

    private static final String SQL_FIND_BY_EMAIL =
        "SELECT customer_id, first_name, last_name, email, phone, national_id, address, created_at " +
        "  FROM customers WHERE email = ?";

    private static final String SQL_FIND_ALL =
        "SELECT customer_id, first_name, last_name, email, phone, national_id, address, created_at " +
        "  FROM customers ORDER BY customer_id ASC LIMIT ?";

    public int createCustomer(String firstName, String lastName, String email,
                              String phone, String nationalId, String address) {
        log.info("Registering customer: {} {}, email={}", firstName, lastName, email);

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, email);
            ps.setString(4, phone);
            ps.setString(5, nationalId);
            ps.setString(6, address);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    log.info("Customer created with ID {}", id);
                    return id;
                }
                throw new BankingException("Customer creation failed – no ID returned.");
            }
        } catch (SQLException e) {
            throw new BankingException("Failed to create customer: " + e.getMessage(), e);
        }
    }

    public Optional<Customer> findById(int customerId) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new BankingException("Error finding customer " + customerId + ": " + e.getMessage(), e);
        }
    }

    public Optional<Customer> findByEmail(String email) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EMAIL)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new BankingException("Error finding customer by email " + email + ": " + e.getMessage(), e);
        }
    }

    public List<Customer> findAll(int limit) {
        List<Customer> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL)) {

            ps.setInt(1, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new BankingException("Error fetching customers: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Customer> findAll() {
        return findAll(100);
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerId(rs.getInt("customer_id"));
        c.setFirstName(rs.getString("first_name"));
        c.setLastName(rs.getString("last_name"));
        c.setEmail(rs.getString("email"));
        c.setPhone(rs.getString("phone"));
        c.setNationalId(rs.getString("national_id"));
        c.setAddress(rs.getString("address"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
        return c;
    }
}
