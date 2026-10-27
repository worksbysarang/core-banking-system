package com.bank.system.api.controller;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.model.Branch;
import com.bank.system.security.guard.SecureBankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * BranchController – exposes branch listing.
 */
public class BranchController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(BranchController.class);

    private final SecureBankingService secureService;

    public BranchController(SecureBankingService secureService) {
        this.secureService = secureService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if ("OPTIONS".equals(method)) {
                HttpUtil.handleOptions(exchange);
                return;
            }

            if (!"GET".equals(method)) {
                HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed – use GET."));
                return;
            }

            List<Branch> branches = secureService.getAllBranches();
            HttpUtil.sendJson(exchange, 200, ApiResponse.ok(branches));
        } catch (Exception e) {
            log.error("BranchController error: {}", e.getMessage(), e);
            HttpUtil.sendJson(exchange, 500, ApiResponse.error("Internal server error."));
        }
    }
}
