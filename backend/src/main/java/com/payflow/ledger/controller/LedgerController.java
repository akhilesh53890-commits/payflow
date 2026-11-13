package com.payflow.ledger.controller;

import com.payflow.common.PageResponse;
import com.payflow.identity.security.PayflowUserPrincipal;
import com.payflow.ledger.dto.LedgerDtos.*;
import com.payflow.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Double-Entry Ledger", description = "Endpoints for immutable double-entry journal transactions, accounts, and derived balances")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List ledger accounts for current merchant or all accounts for admin")
    public ResponseEntity<List<LedgerAccountDto>> listAccounts(@AuthenticationPrincipal PayflowUserPrincipal principal) {
        String merchantId = "ADMIN".equals(principal.getRole()) ? null : principal.getMerchantId();
        return ResponseEntity.ok(ledgerService.listAccounts(merchantId));
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    @Operation(summary = "Get exact mathematically derived balance from immutable ledger entries")
    public ResponseEntity<AccountBalanceDto> getBalance(@PathVariable String accountNumber) {
        return ResponseEntity.ok(ledgerService.getAccountBalance(accountNumber));
    }

    @GetMapping("/transactions")
    @Operation(summary = "List paginated double-entry ledger transactions and balanced entries")
    public ResponseEntity<PageResponse<LedgerTransactionDto>> listTransactions(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.from(ledgerService.listTransactions(pageable)));
    }

    @PostMapping("/journals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Post an administrative double-entry journal transaction")
    public ResponseEntity<LedgerTransactionDto> postJournal(@RequestBody PostJournalCommand command) {
        LedgerTransactionDto dto = ledgerService.postJournal(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}

