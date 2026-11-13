package com.payflow.ledger.domain;

public enum EntryType {
    DEBIT,
    CREDIT;

    public EntryType invert() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}

