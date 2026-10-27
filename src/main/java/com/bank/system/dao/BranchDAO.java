package com.bank.system.dao;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.exception.BankingException;
import com.bank.system.model.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-Access Object for the {@code branches} table.
 */
public class BranchDAO {

    private static final Logger log = LoggerFactory.getLogger(BranchDAO.class);

    private static final String SQL_INSERT =
        "INSERT INTO branches (branch_name, ifsc_code, address) " +
        "VALUES (?, ?, ?) RETURNING branch_id";

    private static final String SQL_FIND_BY_ID =
        "SELECT branch_id, branch_name, ifsc_code, address, created_at " +
        "  FROM branches WHERE branch_id = ?";

    private static final String SQL_FIND_ALL =
        "SELECT branch_id, branch_name, ifsc_code, address, created_at " +
        "  FROM branches ORDER BY branch_id ASC";

    public int createBranch(String branchName, String ifscCode, String address) {
        log.info("Creating branch: {} (IFSC: {})", branchName, ifscCode);

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setString(1, branchName);
            ps.setString(2, ifscCode);
            ps.setString(3, address);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    log.info("Branch created with ID {}", id);
                    return id;
                }
                throw new BankingException("Branch creation failed – no ID returned.");
            }
        } catch (SQLException e) {
            throw new BankingException("Failed to create branch: " + e.getMessage(), e);
        }
    }

    public Optional<Branch> findById(int branchId) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setInt(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new BankingException("Error finding branch " + branchId + ": " + e.getMessage(), e);
        }
    }

    public List<Branch> findAll() {
        List<Branch> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new BankingException("Error fetching branches: " + e.getMessage(), e);
        }
        return list;
    }

    private Branch mapRow(ResultSet rs) throws SQLException {
        Branch b = new Branch();
        b.setBranchId(rs.getInt("branch_id"));
        b.setBranchName(rs.getString("branch_name"));
        b.setIfscCode(rs.getString("ifsc_code"));
        b.setAddress(rs.getString("address"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) b.setCreatedAt(ts.toLocalDateTime());
        return b;
    }
}
