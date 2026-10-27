package com.bank.system.api.controller;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.model.Customer;
import com.bank.system.security.exception.AuthorizationException;
import com.bank.system.security.guard.SecureBankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * CustomerController – manages customer directory and registration.
 *
 * <pre>
 *   GET  /api/customers     – list all customers
 *   POST /api/customers     – create a new customer
 *   GET  /api/customers/{id} – get customer details
 * </pre>
 */
public class CustomerController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final SecureBankingService secureService;

    public CustomerController(SecureBankingService secureService) {
        this.secureService = secureService;
    }

    public static class CreateCustomerRequest {
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String nationalId;
        public String address;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path   = exchange.getRequestURI().getPath();

        try {
            if ("OPTIONS".equals(method)) {
                HttpUtil.handleOptions(exchange);
                return;
            }

            String[] segments = path.split("/");
            if (segments.length == 3) {
                // /api/customers
                if ("GET".equals(method)) {
                    List<Customer> list = secureService.getAllCustomers();
                    HttpUtil.sendJson(exchange, 200, ApiResponse.ok(list));
                } else if ("POST".equals(method)) {
                    handleCreateCustomer(exchange);
                } else {
                    HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed."));
                }
            } else if (segments.length == 4) {
                // /api/customers/{id}
                int customerId = Integer.parseInt(segments[3]);
                if ("GET".equals(method)) {
                    Customer c = secureService.getCustomer(customerId);
                    HttpUtil.sendJson(exchange, 200, ApiResponse.ok(c));
                } else {
                    HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed."));
                }
            } else {
                HttpUtil.sendJson(exchange, 404, ApiResponse.error("Customer route not found."));
            }
        } catch (AuthorizationException e) {
            HttpUtil.sendJson(exchange, 403, ApiResponse.error(e.getMessage()));
        } catch (NumberFormatException e) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error("Invalid customer ID."));
        } catch (com.bank.system.exception.BankingException e) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("CustomerController error: {}", e.getMessage(), e);
            HttpUtil.sendJson(exchange, 500, ApiResponse.error("Internal server error."));
        }
    }

    private void handleCreateCustomer(HttpExchange exchange) throws IOException {
        CreateCustomerRequest req = HttpUtil.readJson(exchange, CreateCustomerRequest.class);
        if (req == null) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error("Request body is required."));
            return;
        }

        int customerId = secureService.createCustomer(
            req.firstName, req.lastName, req.email, req.phone, req.nationalId, req.address);

        HttpUtil.sendJson(exchange, 201, ApiResponse.ok(Map.of(
            "customerId", customerId,
            "message", "Customer registered successfully."
        )));
    }
}
